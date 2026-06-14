package com.artha.kirana.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.HotPink
import com.artha.kirana.ui.theme.Tag
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.util.formatRupees

/** True when the item has a threshold set and has dropped below it. */
fun ItemEntity.isLowStock(): Boolean = reorderThreshold > 0 && qtyInStock < reorderThreshold

@Composable
fun ItemCard(item: ItemEntity, onClick: () -> Unit) {
    val low = item.isLowStock()
    Card(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = HazardWhite,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${trimQty(item.qtyInStock)} ${item.unit.uppercase()}".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (low) HotPink else TextMuted,
                )
                if (item.category != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    formatRupees(item.sellPrice),
                    style = MaterialTheme.typography.headlineSmall,
                    color = HazardWhite,
                )
                if (low) {
                    Tag("LOW", color = HotPink)
                }
            }
        }
    }
}

/** "2" not "2.0"; "1.5" stays "1.5". */
fun trimQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()

package com.artha.kirana.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.Ink
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.SectionTitle
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.Ultraviolet

@Composable
fun InventoryScreen(
    onScanChallan: () -> Unit = {},
    vm: InventoryViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()

    // null = sheet closed; sentinel ADD item (id 0) = add mode; real item = edit mode.
    var sheetItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text("STOCK", style = MaterialTheme.typography.displaySmall, color = HazardWhite)
            Spacer(Modifier.height(10.dp))
            Rule(color = Ultraviolet)
            Spacer(Modifier.height(16.dp))

            SectionTitle("INVENTORY")
            Spacer(Modifier.height(10.dp))

            if (items.isEmpty()) {
                Card {
                    Text(
                        "कोई आइटम नहीं · No items yet. Tap + to add stock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        ItemCard(item = item, onClick = { sheetItem = item; showAdd = false })
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            // Scan challan FAB — opens scan with CHALLAN purpose
            FloatingActionButton(
                onClick = onScanChallan,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                containerColor = Mint,
                contentColor = Ink,
            ) { Icon(Icons.Filled.CameraAlt, contentDescription = "Scan challan") }

            // Add item manually FAB
            FloatingActionButton(
                onClick = { showAdd = true; sheetItem = null },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                containerColor = Mint,
                contentColor = Ink,
            ) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
        }
    }

    if (showAdd || sheetItem != null) {
        AddItemSheet(
            existing = sheetItem,
            onDismiss = { showAdd = false; sheetItem = null },
            onSave = { item ->
                if (item.id == 0L) vm.addItem(item) else vm.saveItem(item)
                showAdd = false; sheetItem = null
            },
            onRestock = { item, add ->
                vm.restock(item, add)
                showAdd = false; sheetItem = null
            },
        )
    }
}

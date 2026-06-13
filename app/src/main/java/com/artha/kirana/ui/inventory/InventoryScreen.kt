package com.artha.kirana.ui.inventory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

@Composable
fun InventoryScreen(vm: InventoryViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()

    // null = sheet closed; sentinel ADD item (id 0) = add mode; real item = edit mode.
    var sheetItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Text(
                "No items yet. Tap + to add stock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ItemCard(item = item, onClick = { sheetItem = item; showAdd = false })
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true; sheetItem = null },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
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

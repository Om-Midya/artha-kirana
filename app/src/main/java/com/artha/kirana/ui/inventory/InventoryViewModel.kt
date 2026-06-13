package com.artha.kirana.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventory: InventoryRepository,
) : ViewModel() {

    val items: StateFlow<List<ItemEntity>> =
        inventory.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addItem(item: ItemEntity) = viewModelScope.launch { inventory.addItem(item) }

    fun saveItem(item: ItemEntity) = viewModelScope.launch { inventory.updateItem(item) }

    fun restock(item: ItemEntity, addQty: Double) = viewModelScope.launch {
        inventory.updateItem(item.copy(qtyInStock = item.qtyInStock + addQty))
    }
}

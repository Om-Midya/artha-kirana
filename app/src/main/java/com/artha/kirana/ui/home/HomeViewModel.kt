package com.artha.kirana.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.SalesRepository
import com.artha.kirana.domain.usecase.EditSaleUseCase
import com.artha.kirana.util.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    sales: SalesRepository,
    private val editSaleUseCase: EditSaleUseCase,
) : ViewModel() {

    private val startOfToday = TimeRange.startOfToday()

    val todayRevenue: StateFlow<Double> =
        sales.revenueBetween(startOfToday, Long.MAX_VALUE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val recentSales: StateFlow<List<SaleEntity>> =
        sales.observeSince(startOfToday)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Apply an edit to a saved sale (reverses old effects, applies new). Reactive via Room flows. */
    fun editSale(old: SaleEntity, edited: SaleEntry) {
        viewModelScope.launch { editSaleUseCase(old, edited) }
    }
}

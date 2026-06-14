package com.artha.kirana.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.SalesRepository
import com.artha.kirana.domain.usecase.EditSaleUseCase
import com.artha.kirana.util.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sales: SalesRepository,
    private val editSaleUseCase: EditSaleUseCase,
) : ViewModel() {

    private val _selectedDay = MutableStateFlow(TimeRange.startOfDay(System.currentTimeMillis()))
    val selectedDay: StateFlow<Long> = _selectedDay.asStateFlow()

    /** End-of-day (inclusive) for the BETWEEN queries. */
    private fun dayEnd(dayStart: Long) = TimeRange.nextDayStart(dayStart) - 1

    val revenue: StateFlow<Double> =
        _selectedDay.flatMapLatest { day -> sales.revenueBetween(day, dayEnd(day)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val daySales: StateFlow<List<SaleEntity>> =
        _selectedDay.flatMapLatest { day -> sales.observeBetween(day, dayEnd(day)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun previousDay() { _selectedDay.value = TimeRange.prevDayStart(_selectedDay.value) }

    fun nextDay() {
        val next = TimeRange.nextDayStart(_selectedDay.value)
        if (next <= TimeRange.startOfDay(System.currentTimeMillis())) _selectedDay.value = next
    }

    /** [utcMillis] from the DatePicker; clamps to today (no future). */
    fun selectDay(utcMillis: Long) {
        val picked = TimeRange.localDayStartFromUtcMillis(utcMillis)
        _selectedDay.value = minOf(picked, TimeRange.startOfDay(System.currentTimeMillis()))
    }

    /** Apply an edit to a saved sale (reverses old effects, applies new). Reactive via Room flows. */
    fun editSale(old: SaleEntity, edited: SaleEntry) {
        viewModelScope.launch { editSaleUseCase(old, edited) }
    }
}

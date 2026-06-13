package com.artha.kirana.ui.pnl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.domain.model.DailyRevenue
import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.domain.model.PnlSummary
import com.artha.kirana.domain.usecase.GetDailyRevenueUseCase
import com.artha.kirana.domain.usecase.GetPnlSummaryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PnlViewModel @Inject constructor(
    getPnl: GetPnlSummaryUseCase,
    getDaily: GetDailyRevenueUseCase,
) : ViewModel() {

    private val _period = MutableStateFlow(PnlPeriod.TODAY)
    val period: StateFlow<PnlPeriod> = _period

    val summary: StateFlow<PnlSummary?> =
        _period.flatMapLatest { getPnl(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val daily: StateFlow<List<DailyRevenue>> =
        getDaily().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectPeriod(p: PnlPeriod) { _period.value = p }
}

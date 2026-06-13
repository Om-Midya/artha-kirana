package com.artha.kirana.ui.khata

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.data.db.entity.KhataTransactionEntity
import com.artha.kirana.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KhataPartyDetailViewModel @Inject constructor(
    private val khata: KhataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val partyId: Long = savedStateHandle.get<Long>("partyId") ?: 0L

    val party: StateFlow<KhataEntity?> =
        khata.observeParty(partyId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactions: StateFlow<List<KhataTransactionEntity>> =
        khata.observeTransactions(partyId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun recordPayment(partyName: String, amount: Double) = viewModelScope.launch {
        khata.applyRepayment(partyName, amount, saleId = null)
    }
}

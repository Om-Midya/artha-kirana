package com.artha.kirana.ui.khata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.db.entity.KhataEntity
import com.artha.kirana.domain.repository.KhataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class KhataViewModel @Inject constructor(
    khata: KhataRepository,
) : ViewModel() {

    val parties: StateFlow<List<KhataEntity>> =
        khata.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalOutstanding: StateFlow<Double> =
        khata.totalOutstanding()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
}

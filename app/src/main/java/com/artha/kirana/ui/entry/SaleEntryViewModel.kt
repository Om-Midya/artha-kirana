package com.artha.kirana.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.usecase.LogSaleUseCase
import com.artha.kirana.domain.usecase.ParseSaleEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SaleEntryUiState {
    data object Idle : SaleEntryUiState
    data object Parsing : SaleEntryUiState
    data class Confirm(val entries: List<SaleEntry>) : SaleEntryUiState
    data class ManualFallback(val reason: String) : SaleEntryUiState
}

sealed interface SaleEntryEvent {
    data class Saved(val count: Int) : SaleEntryEvent
}

@HiltViewModel
class SaleEntryViewModel @Inject constructor(
    private val parseSale: ParseSaleEntryUseCase,
    private val logSale: LogSaleUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<SaleEntryUiState>(SaleEntryUiState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<SaleEntryEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var lastRawText: String = ""

    fun parse(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        lastRawText = trimmed
        _state.value = SaleEntryUiState.Parsing
        viewModelScope.launch {
            val result = parseSale(trimmed)
            _state.value = result.fold(
                onSuccess = { entries ->
                    if (entries.isEmpty()) {
                        SaleEntryUiState.ManualFallback("Couldn't understand that — enter the sale manually.")
                    } else {
                        SaleEntryUiState.Confirm(entries)
                    }
                },
                onFailure = {
                    SaleEntryUiState.ManualFallback("LLM offline — start the server, or enter the sale manually.")
                },
            )
        }
    }

    fun confirm(entries: List<SaleEntry>) {
        viewModelScope.launch {
            entries.forEach { logSale(it, inputMethod = "typed", rawInput = lastRawText) }
            _events.emit(SaleEntryEvent.Saved(entries.size))
            _state.value = SaleEntryUiState.Idle
        }
    }

    fun cancel() {
        _state.value = SaleEntryUiState.Idle
    }

    /** A blank entry for the manual-fallback form. */
    fun blankEntry(): SaleEntry = SaleEntry(item = null, qty = null, amount = null, type = "cash", party = null)
}

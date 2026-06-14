package com.artha.kirana.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.remote.CloudVisionClient
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.usecase.LogPurchaseUseCase
import com.artha.kirana.domain.usecase.LogSaleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class ScanMode { LEDGER, BILL }

sealed class ScanUiState {
    object Idle : ScanUiState()
    object Reading : ScanUiState()
    data class LedgerReview(val entries: List<SaleEntry>) : ScanUiState()
    data class BillReview(val items: List<ParsedPurchaseItem>, val supplier: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
    data class Done(val count: Int) : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val cloudVisionClient: CloudVisionClient,
    private val logSale: LogSaleUseCase,
    private val logPurchase: LogPurchaseUseCase,
) : ViewModel() {

    private val _mode = MutableStateFlow(ScanMode.LEDGER)
    val mode = _mode.asStateFlow()

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state = _state.asStateFlow()

    fun setMode(m: ScanMode) {
        val s = _state.value
        if (s is ScanUiState.Idle || s is ScanUiState.Error) {
            _mode.value = m
        }
    }

    fun onImageCaptured(base64: String) {
        if (_state.value is ScanUiState.Reading) return
        _state.value = ScanUiState.Reading
        viewModelScope.launch {
            try {
                when (_mode.value) {
                    ScanMode.LEDGER -> {
                        val entries = cloudVisionClient.extractLedger(base64)
                        _state.value = if (entries.isEmpty()) {
                            ScanUiState.Error(
                                "कुछ पढ़ा नहीं गया — साफ़ फ़ोटो लें / Couldn't read it — take a clearer photo"
                            )
                        } else {
                            ScanUiState.LedgerReview(entries)
                        }
                    }
                    ScanMode.BILL -> {
                        val bill = cloudVisionClient.extractBill(base64)
                        _state.value = if (bill.items.isEmpty()) {
                            ScanUiState.Error(
                                "कुछ पढ़ा नहीं गया — साफ़ फ़ोटो लें / Couldn't read it — take a clearer photo"
                            )
                        } else {
                            ScanUiState.BillReview(bill.items, "")
                        }
                    }
                }
            } catch (t: Throwable) {
                Timber.e(t, "ScanViewModel: image extraction failed")
                _state.value = ScanUiState.Error(t.message ?: "Unknown error — please try again")
            }
        }
    }

    // ── Ledger editing ────────────────────────────────────────────────────────

    fun updateEntry(index: Int, entry: SaleEntry) {
        val current = _state.value as? ScanUiState.LedgerReview ?: return
        val updated = current.entries.toMutableList()
        if (index in updated.indices) {
            updated[index] = entry
            _state.value = current.copy(entries = updated)
        }
    }

    fun removeEntry(index: Int) {
        val current = _state.value as? ScanUiState.LedgerReview ?: return
        val updated = current.entries.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _state.value = current.copy(entries = updated)
        }
    }

    // ── Bill editing ──────────────────────────────────────────────────────────

    fun updateItem(index: Int, item: ParsedPurchaseItem) {
        val current = _state.value as? ScanUiState.BillReview ?: return
        val updated = current.items.toMutableList()
        if (index in updated.indices) {
            updated[index] = item
            _state.value = current.copy(items = updated)
        }
    }

    fun removeItem(index: Int) {
        val current = _state.value as? ScanUiState.BillReview ?: return
        val updated = current.items.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _state.value = current.copy(items = updated)
        }
    }

    fun setSupplier(supplier: String) {
        val current = _state.value as? ScanUiState.BillReview ?: return
        _state.value = current.copy(supplier = supplier)
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    fun confirm() {
        viewModelScope.launch {
            when (val s = _state.value) {
                is ScanUiState.LedgerReview -> {
                    var count = 0
                    s.entries.forEach { entry ->
                        try {
                            logSale(entry, inputMethod = "scan", rawInput = null)
                            count++
                        } catch (t: Throwable) {
                            Timber.e(t, "ScanViewModel: logSale failed for entry $entry")
                        }
                    }
                    _state.value = ScanUiState.Done(count)
                }
                is ScanUiState.BillReview -> {
                    try {
                        val count = logPurchase(s.items, s.supplier.ifBlank { null })
                        _state.value = ScanUiState.Done(count)
                    } catch (t: Throwable) {
                        Timber.e(t, "ScanViewModel: logPurchase failed")
                        _state.value = ScanUiState.Error(
                            t.message ?: "Failed to save — please try again"
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun reset() {
        _state.value = ScanUiState.Idle
    }
}

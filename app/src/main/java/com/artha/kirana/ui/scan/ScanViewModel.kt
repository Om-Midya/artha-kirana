package com.artha.kirana.ui.scan

import androidx.lifecycle.SavedStateHandle
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

enum class ScanPurpose { SALES, CHALLAN }

enum class SalesMode { CUSTOMER_BILL, DAY_SCRIBBLE }

data class ChallanLine(
    val name: String,
    val qty: Double,
    val unit: String,
    val unitPrice: Double?,
    val amount: Double?,
    val sellPrice: String,  // editable text, blank initially
)

sealed class ScanUiState {
    object Idle : ScanUiState()
    object Reading : ScanUiState()
    data class LedgerReview(val entries: List<SaleEntry>, val customer: String) : ScanUiState()
    data class ChallanReview(val items: List<ChallanLine>, val supplier: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
    data class Done(val count: Int) : ScanUiState()
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val cloudVisionClient: CloudVisionClient,
    private val logSale: LogSaleUseCase,
    private val logPurchase: LogPurchaseUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val purpose: ScanPurpose =
        if (savedStateHandle.get<String>("purpose") == "challan") ScanPurpose.CHALLAN
        else ScanPurpose.SALES

    private val _salesMode = MutableStateFlow(SalesMode.CUSTOMER_BILL)
    val salesMode = _salesMode.asStateFlow()

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state = _state.asStateFlow()

    fun setSalesMode(m: SalesMode) {
        val s = _state.value
        if (s is ScanUiState.Idle || s is ScanUiState.Error) {
            _salesMode.value = m
        }
    }

    fun onImageCaptured(base64: String) {
        if (_state.value is ScanUiState.Reading) return
        _state.value = ScanUiState.Reading
        viewModelScope.launch {
            try {
                when (purpose) {
                    ScanPurpose.SALES -> {
                        val entries = cloudVisionClient.extractLedger(base64)
                        if (entries.isEmpty()) {
                            _state.value = ScanUiState.Error(
                                "कुछ पढ़ा नहीं गया — साफ़ फ़ोटो लें / Couldn't read it"
                            )
                        } else {
                            val mode = _salesMode.value
                            if (mode == SalesMode.CUSTOMER_BILL) {
                                val customer = entries.firstOrNull()?.party ?: ""
                                val mapped = entries.map { entry ->
                                    entry.copy(
                                        party = customer.ifBlank { entry.party },
                                        type = if (entry.type == "repayment") entry.type else "credit",
                                    )
                                }
                                _state.value = ScanUiState.LedgerReview(mapped, customer)
                            } else {
                                // DAY_SCRIBBLE: entries as-is, no forced customer
                                _state.value = ScanUiState.LedgerReview(entries, "")
                            }
                        }
                    }
                    ScanPurpose.CHALLAN -> {
                        val bill = cloudVisionClient.extractBill(base64)
                        if (bill.items.isEmpty()) {
                            _state.value = ScanUiState.Error(
                                "कुछ पढ़ा नहीं गया — साफ़ फ़ोटो लें / Couldn't read it"
                            )
                        } else {
                            val lines = bill.items.map { item ->
                                ChallanLine(
                                    name = item.name,
                                    qty = item.qty,
                                    unit = item.unit,
                                    unitPrice = item.unitPrice,
                                    amount = item.amount,
                                    sellPrice = "",
                                )
                            }
                            _state.value = ScanUiState.ChallanReview(lines, "")
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

    fun setCustomer(name: String) {
        val current = _state.value as? ScanUiState.LedgerReview ?: return
        // In CUSTOMER_BILL mode, propagate customer name to every row's party field
        val updatedEntries = if (_salesMode.value == SalesMode.CUSTOMER_BILL) {
            current.entries.map { it.copy(party = name) }
        } else {
            current.entries
        }
        _state.value = current.copy(entries = updatedEntries, customer = name)
    }

    // ── Challan editing ───────────────────────────────────────────────────────

    fun updateChallanLine(index: Int, line: ChallanLine) {
        val current = _state.value as? ScanUiState.ChallanReview ?: return
        val updated = current.items.toMutableList()
        if (index in updated.indices) {
            updated[index] = line
            _state.value = current.copy(items = updated)
        }
    }

    fun removeChallanLine(index: Int) {
        val current = _state.value as? ScanUiState.ChallanReview ?: return
        val updated = current.items.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            _state.value = current.copy(items = updated)
        }
    }

    fun setSupplier(supplier: String) {
        val current = _state.value as? ScanUiState.ChallanReview ?: return
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
                is ScanUiState.ChallanReview -> {
                    try {
                        val purchaseItems = s.items.map { line ->
                            ParsedPurchaseItem(
                                name = line.name,
                                qty = line.qty,
                                unit = line.unit,
                                unitPrice = line.unitPrice,
                                amount = line.amount,
                                sellPrice = line.sellPrice.toDoubleOrNull(),
                            )
                        }
                        val count = logPurchase(purchaseItems, s.supplier.ifBlank { null })
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

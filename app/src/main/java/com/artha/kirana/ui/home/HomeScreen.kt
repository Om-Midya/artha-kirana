package com.artha.kirana.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.data.db.entity.SaleEntity
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.GhostButton
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Line
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.PrimaryButton
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.SectionTitle
import com.artha.kirana.ui.theme.Tag
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.TileYellow
import com.artha.kirana.ui.theme.Ultraviolet
import com.artha.kirana.util.TimeRange
import com.artha.kirana.util.formatRupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    val revenue by vm.revenue.collectAsStateWithLifecycle()
    val sales by vm.daySales.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SaleEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedDay by vm.selectedDay.collectAsStateWithLifecycle()
    val today = TimeRange.startOfToday()
    val isToday = selectedDay == today
    val canGoNext = selectedDay < today
    var showCalendar by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        // Wordmark row — Anton shout + offline tag.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ARTHA", style = MaterialTheme.typography.displaySmall, color = HazardWhite)
            Tag("OFFLINE-FIRST", color = Mint)
        }
        Spacer(Modifier.height(10.dp))
        Rule(color = Ultraviolet) // today bar
        DayNavBar(
            label = if (isToday) "आज · TODAY" else TimeRange.dayLabel(selectedDay),
            canGoNext = canGoNext,
            onPrev = vm::previousDay,
            onNext = vm::nextDay,
            onPickDate = { showCalendar = true },
        )
        Spacer(Modifier.height(16.dp))

        // Today hero — feature card, mono kicker + Anton ₹ number in mint.
        Card(feature = true) {
            Kicker(
                if (isToday) "आज की बिक्री · TODAY" else "बिक्री · ${TimeRange.dayLabel(selectedDay)}",
                color = Ultraviolet,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                formatRupees(revenue),
                style = MaterialTheme.typography.displayLarge,
                color = Mint,
            )
            Spacer(Modifier.height(4.dp))
            SectionTitle("GROSS · EXCLUDES REPAYMENTS")
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("RECENT ENTRIES")
        Spacer(Modifier.height(10.dp))

        if (sales.isEmpty()) {
            Card {
                Text(
                    "इस दिन कोई बिक्री नहीं · No sales on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sales) { sale -> SaleRow(sale, onEdit = { editing = it }) }
            }
        }
    }

    val sale = editing
    if (sale != null) {
        var draft by remember(sale.id) { mutableStateOf(sale.toEntry()) }
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                Kicker("बदलें · EDIT ENTRY")
                Spacer(Modifier.height(14.dp))
                EditableEntryCard(draft) { draft = it }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton("Cancel", onClick = { editing = null })
                    PrimaryButton("Save", onClick = {
                        vm.editSale(sale, draft)
                        editing = null
                    })
                }
            }
        }
    }

    if (showCalendar) {
        val todayMs = remember { System.currentTimeMillis() }
        val pickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMs
            },
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                PrimaryButton("OK", onClick = {
                    pickerState.selectedDateMillis?.let { vm.selectDay(it) }
                    showCalendar = false
                })
            },
            dismissButton = { GhostButton("Cancel", onClick = { showCalendar = false }) },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayNavBar(
    label: String,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day", tint = Mint)
        }
        Row(
            modifier = Modifier
                .border(1.dp, Line, RoundedCornerShape(20.dp))
                .clickable { onPickDate() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = HazardWhite)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Pick a date", tint = Mint)
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Next day",
                tint = if (canGoNext) Mint else TextMuted,
            )
        }
    }
}

@Composable
private fun SaleRow(sale: SaleEntity, onEdit: (SaleEntity) -> Unit) {
    Card(modifier = Modifier.clickable { onEdit(sale) }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sale.itemName ?: "Sale",
                    style = MaterialTheme.typography.titleMedium,
                    color = HazardWhite,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    saleSubtitle(sale).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor(sale.type),
                )
            }
            Text(
                formatRupees(sale.amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = typeColor(sale.type),
            )
        }
    }
}

/** Money semantics: credit/udhaar = yellow · repayment & cash (money in) = mint. */
private fun typeColor(type: String) = when (type) {
    "credit" -> TileYellow
    "repayment" -> Mint
    else -> Mint
}

/** "Cash sale" / "Credit · Ramesh" / "Repayment · Ramesh". Customer appended only when present. */
private fun saleSubtitle(sale: SaleEntity): String {
    val typeLabel = when (sale.type) {
        "cash" -> "Cash sale"
        "credit" -> "Credit"
        "repayment" -> "Repayment"
        else -> sale.type.replaceFirstChar { it.uppercase() }
    }
    return sale.party?.let { "$typeLabel · $it" } ?: typeLabel
}

/** Map a saved sale to the editable [SaleEntry] form for the edit sheet. */
private fun SaleEntity.toEntry(): SaleEntry = SaleEntry(
    item = itemName,
    qty = when {
        qtySold == 0.0 -> null
        qtySold % 1.0 == 0.0 -> qtySold.toLong().toString()
        else -> qtySold.toString()
    },
    amount = amount,
    type = type,
    party = party,
)

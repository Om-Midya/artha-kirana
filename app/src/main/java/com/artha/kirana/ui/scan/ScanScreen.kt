package com.artha.kirana.ui.scan

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.Canvas
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.GhostButton
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.HotPink
import com.artha.kirana.ui.theme.Ink
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Line
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.PrimaryButton
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.Tag
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.Ultraviolet
import com.artha.kirana.util.ImageUtils
import androidx.compose.ui.platform.LocalContext

const val ROUTE_SCAN = "scan"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    onDone: () -> Unit,
    vm: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val salesMode by vm.salesMode.collectAsStateWithLifecycle()

    // iQOO / vivo camera hardening: persist the pending URI across process death.
    // The camera app can cold-restart this activity; SharedPreferences survives that.
    val prefs = remember {
        context.getSharedPreferences("artha_scan", android.content.Context.MODE_PRIVATE)
    }
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Read the captured JPEG and hand it to the VM — gated on "pending_uri" presence.
    fun processCapture() {
        if (state is ScanUiState.Reading) return
        val pendingStr = prefs.getString("pending_uri", null) ?: return
        val uri = captureUri ?: Uri.parse(pendingStr)
        if (captureUri == null) captureUri = uri
        val b64 = ImageUtils.uriToBase64(context, uri, maxDim = 1568, quality = 90)
        if (b64 != null) {
            prefs.edit().remove("pending_uri").apply()
            vm.onImageCaptured(b64)
        }
    }

    // Primary result callback — we IGNORE the success boolean (vivo returns false even when
    // the JPEG was written) and read the file directly.
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { _ -> processCapture() }

    // Fallback 1: ON_RESUME — fires when we return from the camera app even if the
    // result callback was suppressed by the OS.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) processCapture()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Fallback 2: first composition after a cold restart — process any orphaned capture.
    LaunchedEffect(Unit) { processCapture() }

    // Navigate away when confirmed.
    LaunchedEffect(state) {
        if (state is ScanUiState.Done) onDone()
    }

    Scaffold(
        containerColor = Canvas,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "SCAN",
                    style = MaterialTheme.typography.displaySmall,
                    color = HazardWhite,
                )
                if (vm.purpose == ScanPurpose.SALES) {
                    Tag("बिक्री / SALES", color = Ultraviolet)
                } else {
                    Tag("चालान / CHALLAN", color = Mint)
                }
            }
            Rule(color = Ultraviolet)

            // ── Sales sub-mode toggle (only for SALES purpose) ─────────────
            if (vm.purpose == ScanPurpose.SALES) {
                val isIdle = state is ScanUiState.Idle || state is ScanUiState.Error
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeToggleChip(
                        label = "CUSTOMER BILL",
                        selected = salesMode == SalesMode.CUSTOMER_BILL,
                        enabled = isIdle,
                        onClick = { vm.setSalesMode(SalesMode.CUSTOMER_BILL) },
                    )
                    ModeToggleChip(
                        label = "DAY SCRIBBLE",
                        selected = salesMode == SalesMode.DAY_SCRIBBLE,
                        enabled = isIdle,
                        onClick = { vm.setSalesMode(SalesMode.DAY_SCRIBBLE) },
                    )
                }
            }

            // ── Capture button ─────────────────────────────────────────────────
            PrimaryButton(
                text = "फ़ोटो लें / CAPTURE",
                enabled = state !is ScanUiState.Reading,
                onClick = {
                    if (!cameraPermission.status.isGranted) {
                        cameraPermission.launchPermissionRequest()
                    } else {
                        val uri = ImageUtils.newImageUri(context)
                        captureUri = uri
                        prefs.edit().putString("pending_uri", uri.toString()).apply()
                        takePicture.launch(uri)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── State content ──────────────────────────────────────────────────
            when (val s = state) {
                is ScanUiState.Idle -> {
                    Kicker(
                        text = when {
                            vm.purpose == ScanPurpose.CHALLAN ->
                                "चालान की फ़ोटो लें → स्टॉक अपडेट होगा"
                            salesMode == SalesMode.CUSTOMER_BILL ->
                                "ग्राहक के बिल की फ़ोटो लें → उधार खाते में जाएगा"
                            else ->
                                "बही-खाता की फ़ोटो लें → खाता भर जाएगा"
                        },
                        color = TextMuted,
                    )
                }

                is ScanUiState.Reading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Mint,
                        )
                        Kicker("पढ़ा जा रहा है… (cloud)", color = TextMuted)
                    }
                }

                is ScanUiState.Error -> {
                    Card(feature = false) {
                        Kicker("त्रुटि / ERROR", color = HotPink)
                        Spacer(Modifier.height(6.dp))
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = HazardWhite)
                        Spacer(Modifier.height(10.dp))
                        GhostButton(
                            text = "रीसेट / RESET",
                            onClick = { vm.reset() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is ScanUiState.LedgerReview -> {
                    Kicker(
                        "${s.entries.size} ENTRIES FOUND · बही-खाता",
                        color = Mint,
                    )
                    // Customer field — only shown in CUSTOMER_BILL mode
                    if (salesMode == SalesMode.CUSTOMER_BILL) {
                        OutlinedTextField(
                            value = s.customer,
                            onValueChange = { vm.setCustomer(it) },
                            label = { Text("ग्राहक / Customer") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                    s.entries.forEachIndexed { index, entry ->
                        LedgerEntryRow(
                            entry = entry,
                            onChanged = { vm.updateEntry(index, it) },
                            onDelete = { vm.removeEntry(index) },
                        )
                    }
                    PrimaryButton(
                        text = "खाते में डालें / CONFIRM (${s.entries.size})",
                        enabled = s.entries.isNotEmpty(),
                        onClick = { vm.confirm() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GhostButton(
                        text = "रद्द करें / CANCEL",
                        onClick = { vm.reset() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ScanUiState.ChallanReview -> {
                    Kicker(
                        "${s.items.size} ITEMS FOUND · चालान",
                        color = Mint,
                    )
                    // Supplier field
                    OutlinedTextField(
                        value = s.supplier,
                        onValueChange = { vm.setSupplier(it) },
                        label = { Text("सप्लायर / Supplier (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    s.items.forEachIndexed { index, line ->
                        ChallanLineRow(
                            line = line,
                            onChanged = { vm.updateChallanLine(index, it) },
                            onDelete = { vm.removeChallanLine(index) },
                        )
                    }
                    PrimaryButton(
                        text = "स्टॉक में डालें / CONFIRM (${s.items.size})",
                        enabled = s.items.isNotEmpty(),
                        onClick = { vm.confirm() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GhostButton(
                        text = "रद्द करें / CANCEL",
                        onClick = { vm.reset() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is ScanUiState.Done -> {
                    // LaunchedEffect above calls onDone(); show brief confirmation while navigating
                    Kicker("✓ ${s.count} ENTRIES SAVED", color = Mint)
                }
            }
        }
    }
}

// ── Mode toggle chip ──────────────────────────────────────────────────────────

@Composable
private fun ModeToggleChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) Mint else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Line),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Ink else TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// ── Ledger entry row (EditableEntryCard + delete) ─────────────────────────────

@Composable
private fun LedgerEntryRow(
    entry: SaleEntry,
    onChanged: (SaleEntry) -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete entry",
                    tint = HotPink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        EditableEntryCard(entry = entry, onChange = onChanged)
    }
}

// ── Challan line row (name / qty / cost read-only / sell price editable + delete) ──

@Composable
private fun ChallanLineRow(
    line: ChallanLine,
    onChanged: (ChallanLine) -> Unit,
    onDelete: () -> Unit,
) {
    Card(feature = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.name,
                    onValueChange = { onChanged(line.copy(name = it)) },
                    label = { Text("Item · वस्तु") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (line.qty == line.qty.toLong().toDouble())
                            line.qty.toLong().toString()
                        else
                            line.qty.toString(),
                        onValueChange = { onChanged(line.copy(qty = it.toDoubleOrNull() ?: line.qty)) },
                        label = { Text("Qty") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = line.unit,
                        onValueChange = { onChanged(line.copy(unit = it)) },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Cost — read-only, from the bill
                    OutlinedTextField(
                        value = line.unitPrice?.let {
                            if (it == it.toLong().toDouble()) "₹${it.toLong()}" else "₹$it"
                        } ?: "—",
                        onValueChange = {},
                        label = { Text("Cost (bill)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true,
                        enabled = false,
                    )
                    // Sell price — editable, blank initially
                    OutlinedTextField(
                        value = line.sellPrice,
                        onValueChange = { onChanged(line.copy(sellPrice = it)) },
                        label = { Text("Sell ₹") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("—", color = TextMuted) },
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete item",
                    tint = HotPink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

package com.artha.kirana.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.theme.AccentGreen
import com.artha.kirana.ui.theme.AccentRed
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SaleEntryScreen(
    onDone: () -> Unit,
    vm: SaleEntryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Saved -> pop back to Home (which updates live via Flow).
    LaunchedEffect(Unit) {
        vm.events.collect { onDone() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Sale · नई बिक्री") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            var input by rememberSaveable { mutableStateOf("") }

            // Transcribed speech drops into the input field, then flows through the same Parse path.
            LaunchedEffect(Unit) {
                vm.transcript.collect { text -> if (text.isNotBlank()) input = text }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Speak it as you'd say it") },
                placeholder = { Text("दो किलो चावल अस्सी रुपये उधार रमेश") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.voiceEnabled) {
                    val isRecording = voice is VoiceState.Recording
                    FilledIconButton(
                        onClick = {
                            if (micPermission.status.isGranted) vm.toggleVoice() else micPermission.launchPermissionRequest()
                        },
                        enabled = voice !is VoiceState.Transcribing,
                        colors = if (isRecording) {
                            IconButtonDefaults.filledIconButtonColors(containerColor = AccentRed)
                        } else {
                            IconButtonDefaults.filledIconButtonColors()
                        },
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Record voice",
                        )
                    }
                }
                Button(
                    onClick = { vm.parse(input) },
                    enabled = state !is SaleEntryUiState.Parsing && input.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Parse") }
            }

            VoiceStatus(voice)

            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                SaleEntryUiState.Idle -> Unit

                SaleEntryUiState.Parsing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("समझ रहा हूँ…")
                }

                is SaleEntryUiState.Confirm -> ConfirmSection(
                    entries = s.entries,
                    onConfirm = { vm.confirm(it) },
                    onCancel = { vm.cancel() },
                )

                is SaleEntryUiState.ManualFallback -> ManualSection(
                    reason = s.reason,
                    blank = vm.blankEntry(),
                    onSave = { vm.confirm(listOf(it)) },
                    onCancel = { vm.cancel() },
                )
            }
        }
    }
}

@Composable
private fun VoiceStatus(voice: VoiceState) {
    val text = when (voice) {
        VoiceState.Recording -> "🎙️ सुन रहा हूँ… (रोकने के लिए फिर दबाएँ)"
        VoiceState.Transcribing -> "लिख रहा हूँ…"
        is VoiceState.Error -> voice.message
        VoiceState.Idle -> null
    }
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (voice is VoiceState.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ConfirmSection(
    entries: List<SaleEntry>,
    onConfirm: (List<SaleEntry>) -> Unit,
    onCancel: () -> Unit,
) {
    var edited by remember(entries) { mutableStateOf(entries) }
    Text("Confirm the sale", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    edited.forEachIndexed { index, entry ->
        EditableEntryCard(entry) { updated ->
            edited = edited.toMutableList().also { it[index] = updated }
        }
        Spacer(Modifier.height(8.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Button(
            onClick = { onConfirm(edited) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        ) { Text("Confirm") }
    }
}

@Composable
private fun ManualSection(
    reason: String,
    blank: SaleEntry,
    onSave: (SaleEntry) -> Unit,
    onCancel: () -> Unit,
) {
    var entry by remember { mutableStateOf(blank) }
    Text(reason, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
    EditableEntryCard(entry) { entry = it }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Button(
            onClick = { onSave(entry) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
        ) { Text("Save") }
    }
}

@Composable
private fun EditableEntryCard(entry: SaleEntry, onChange: (SaleEntry) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = entry.item ?: "",
                onValueChange = { onChange(entry.copy(item = it.ifBlank { null })) },
                label = { Text("Item · वस्तु") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = entry.qty ?: "",
                    onValueChange = { onChange(entry.copy(qty = it.ifBlank { null })) },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = entry.amount?.toLong()?.toString() ?: "",
                    onValueChange = { onChange(entry.copy(amount = it.toDoubleOrNull())) },
                    label = { Text("₹ Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cash", "credit", "repayment").forEach { t ->
                    FilterChip(
                        selected = entry.type == t,
                        onClick = { onChange(entry.copy(type = t)) },
                        label = { Text(t) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = entry.party ?: "",
                onValueChange = { onChange(entry.copy(party = it.ifBlank { null })) },
                label = { Text("Party · ग्राहक (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

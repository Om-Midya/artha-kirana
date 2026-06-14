package com.artha.kirana.ui.entry

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.ui.common.EditableEntryCard
import com.artha.kirana.ui.theme.Canvas
import com.artha.kirana.ui.theme.Card
import com.artha.kirana.ui.theme.GhostButton
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.HotPink
import com.artha.kirana.ui.theme.Ink
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.PrimaryButton
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.Slate
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.Ultraviolet
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

    // Pre-load the whisper model as soon as the screen opens (first transcription stays snappy).
    LaunchedEffect(Unit) { vm.warmUpVoice() }

    // Saved -> pop back to Home (which updates live via Flow).
    LaunchedEffect(Unit) {
        vm.events.collect { onDone() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NEW SALE · नई बिक्री".uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = HazardWhite,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Mint,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Canvas,
                    titleContentColor = HazardWhite,
                    navigationIconContentColor = Mint,
                ),
            )
        },
        containerColor = Canvas,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            var input by rememberSaveable { mutableStateOf("") }

            // Transcribed speech drops into the input field, then flows through the same Parse path.
            LaunchedEffect(Unit) {
                vm.transcript.collect { text -> if (text.isNotBlank()) input = text }
            }

            Spacer(Modifier.height(8.dp))
            Rule(color = Ultraviolet)
            Spacer(Modifier.height(16.dp))

            // Kicker label for the input area
            Kicker("बोलें या लिखें · SPEAK OR TYPE")
            Spacer(Modifier.height(10.dp))

            // Input field — Slate container, no underline
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        "दो किलो चावल अस्सी रुपये उधार रमेश",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = HazardWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Slate,
                    unfocusedContainerColor = Slate,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Mint,
                    focusedTextColor = HazardWhite,
                    unfocusedTextColor = HazardWhite,
                ),
                minLines = 2,
            )

            Spacer(Modifier.height(12.dp))

            // Action row: mic + parse
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.voiceEnabled) {
                    val isRecording = voice is VoiceState.Recording
                    IconButton(
                        onClick = {
                            if (micPermission.status.isGranted) vm.toggleVoice()
                            else micPermission.launchPermissionRequest()
                        },
                        enabled = voice !is VoiceState.Transcribing,
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                if (isRecording) HotPink else Mint,
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Record voice",
                            tint = Ink,
                        )
                    }
                }

                PrimaryButton(
                    text = "Parse",
                    onClick = { vm.parse(input) },
                    enabled = state !is SaleEntryUiState.Parsing && input.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }

            VoiceStatus(voice)

            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                SaleEntryUiState.Idle -> Unit

                SaleEntryUiState.Parsing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Kicker("समझ रहा हूँ…", color = Mint)
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

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VoiceStatus(voice: VoiceState) {
    val (text, isError) = when (voice) {
        VoiceState.Recording -> "सुन रहा हूँ… · RECORDING — TAP TO STOP" to false
        VoiceState.Transcribing -> "लिख रहा हूँ… · TRANSCRIBING" to false
        is VoiceState.Error -> voice.message.uppercase() to true
        VoiceState.Idle -> null to false
    }
    if (text != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) HotPink else Mint,
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

    Card {
        Kicker("बिक्री की पुष्टि करें · CONFIRM SALE")
        Spacer(Modifier.height(10.dp))
        Rule(color = Mint, thickness = 1)
        Spacer(Modifier.height(10.dp))
        edited.forEachIndexed { index, entry ->
            EditableEntryCard(entry) { updated ->
                edited = edited.toMutableList().also { it[index] = updated }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton("Confirm", onClick = { onConfirm(edited) }, modifier = Modifier.weight(1f))
        }
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

    // Error kicker for the failure reason
    Text(
        reason.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = HotPink,
    )
    Spacer(Modifier.height(8.dp))

    Card {
        Kicker("मैन्युअल एंट्री · MANUAL ENTRY", color = TextMuted)
        Spacer(Modifier.height(10.dp))
        Rule(color = HotPink, thickness = 1)
        Spacer(Modifier.height(10.dp))
        EditableEntryCard(entry) { entry = it }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PrimaryButton("Save", onClick = { onSave(entry) }, modifier = Modifier.weight(1f))
        }
    }
}

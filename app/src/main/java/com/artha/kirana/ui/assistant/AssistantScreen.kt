package com.artha.kirana.ui.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.ui.entry.VoiceState
import com.artha.kirana.ui.theme.Canvas
import com.artha.kirana.ui.theme.HazardWhite
import com.artha.kirana.ui.theme.HotPink
import com.artha.kirana.ui.theme.Ink
import com.artha.kirana.ui.theme.Kicker
import com.artha.kirana.ui.theme.Line
import com.artha.kirana.ui.theme.Mint
import com.artha.kirana.ui.theme.Rule
import com.artha.kirana.ui.theme.Slate
import com.artha.kirana.ui.theme.Tag
import com.artha.kirana.ui.theme.TextMuted
import com.artha.kirana.ui.theme.Ultraviolet

private val EXAMPLES = listOf("दो किलो चावल ₹80", "रमेश ने ₹50 दिए", "आज की कमाई?")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: AssistantViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val routing by viewModel.routing.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleVoice() }

    LaunchedEffect(Unit) {
        viewModel.warmUpLlm()
        viewModel.warmUpVoice()
    }
    LaunchedEffect(Unit) { viewModel.transcript.collect { input = it } }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas),
    ) {
        // ── Header bar ─────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ASSISTANT", style = MaterialTheme.typography.displaySmall, color = HazardWhite)
                Tag("AI · ON DEVICE", color = Mint)
            }
            Spacer(Modifier.height(10.dp))
            Rule(color = Ultraviolet)
            Spacer(Modifier.height(8.dp))
        }

        // ── Message list / empty state ──────────────────────────────────────
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            if (messages.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "नमस्ते 🙏",
                        style = MaterialTheme.typography.displaySmall,
                        color = HazardWhite,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "कुछ भी पूछें या बोलें".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EXAMPLES.forEach { ex ->
                            Box(modifier = Modifier.clickable { viewModel.send(ex) }) {
                                Tag(text = ex, color = Mint)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { m ->
                        when (m) {
                            is ChatMessage.User -> UserBubble(m.text)
                            is ChatMessage.Reply -> ReplyBubble(m.text)
                            is ChatMessage.SaleDraft -> SaleDraftBubble(
                                entries = m.entries,
                                status = m.status,
                                onConfirm = { viewModel.confirmSale(m.id, it) },
                                onCancel = { viewModel.cancel(m.id) },
                            )
                            is ChatMessage.PaymentDraft -> PaymentDraftBubble(
                                party = m.party,
                                amount = m.amount,
                                status = m.status,
                                onConfirm = { p, a -> viewModel.confirmPayment(m.id, p, a) },
                                onCancel = { viewModel.cancel(m.id) },
                            )
                            is ChatMessage.PnlAnswer -> PnlAnswerBubble(m.summary)
                        }
                    }
                }
            }
        }

        // ── Routing / transcribing indicator ───────────────────────────────
        if (routing || voice is VoiceState.Transcribing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Kicker(
                    text = if (voice is VoiceState.Transcribing) "सुन रहा हूँ…" else "सोच रहा हूँ…",
                    color = Mint,
                )
            }
        }

        // ── Voice error ─────────────────────────────────────────────────────
        (voice as? VoiceState.Error)?.let { err ->
            Text(
                err.message.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = HotPink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        // ── Input row ───────────────────────────────────────────────────────
        Rule(color = Line, thickness = 1)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Slate)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "लिखें या बोलें…".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = HazardWhite),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Mint,
                    focusedTextColor = HazardWhite,
                    unfocusedTextColor = HazardWhite,
                ),
            )

            if (viewModel.voiceEnabled) {
                val isRecording = voice is VoiceState.Recording
                IconButton(
                    onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isRecording) HotPink else Mint,
                            CircleShape,
                        ),
                ) {
                    Icon(
                        if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Stop recording" else "Voice input",
                        tint = Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(Mint, CircleShape),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

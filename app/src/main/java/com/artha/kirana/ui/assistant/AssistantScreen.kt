package com.artha.kirana.ui.assistant

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.ui.entry.VoiceState

private val EXAMPLES = listOf("दो किलो चावल ₹80", "रमेश ने ₹50 दिए", "आज की कमाई?")

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

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("नमस्ते 🙏 कुछ भी पूछें या बोलें")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EXAMPLES.forEach { ex ->
                            AssistChip(onClick = { viewModel.send(ex) }, label = { Text(ex) })
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

        if (routing || voice is VoiceState.Transcribing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(16.dp).padding(end = 8.dp))
                Text(if (voice is VoiceState.Transcribing) "सुन रहा हूँ…" else "सोच रहा हूँ…")
            }
        }
        (voice as? VoiceState.Error)?.let { Text(it.message) }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("लिखें या बोलें…") },
            )
            if (viewModel.voiceEnabled) {
                IconButton(onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Icon(
                        if (voice is VoiceState.Recording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Voice",
                    )
                }
            }
            IconButton(onClick = {
                viewModel.send(input)
                input = ""
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

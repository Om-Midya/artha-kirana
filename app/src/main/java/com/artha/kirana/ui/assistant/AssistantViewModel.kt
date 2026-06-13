package com.artha.kirana.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.voice.AudioRecorder
import com.artha.kirana.data.voice.WhisperEngine
import com.artha.kirana.domain.model.AssistantResult
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.repository.KhataRepository
import com.artha.kirana.domain.usecase.LogSaleUseCase
import com.artha.kirana.domain.usecase.RouteAssistantUseCase
import com.artha.kirana.ui.entry.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val route: RouteAssistantUseCase,
    private val logSale: LogSaleUseCase,
    private val khata: KhataRepository,
    private val audioRecorder: AudioRecorder,
    private val whisper: WhisperEngine,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _routing = MutableStateFlow(false)
    val routing = _routing.asStateFlow()

    private val _voice = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voice = _voice.asStateFlow()

    /** Emitted when a recording is transcribed — the screen drops this into the input field. */
    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transcript = _transcript.asSharedFlow()

    val voiceEnabled: Boolean get() = whisper.voiceEnabled

    private val ids = AtomicLong(0)
    private val recording = AtomicBoolean(false)

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        append(ChatMessage.User(ids.incrementAndGet(), trimmed))
        _routing.value = true
        viewModelScope.launch {
            val result = route(trimmed)
            _routing.value = false
            append(result.toMessage(ids.incrementAndGet()))
        }
    }

    fun confirmSale(messageId: Long, entries: List<SaleEntry>) {
        viewModelScope.launch {
            entries.forEach { logSale(it, inputMethod = "typed", rawInput = null) }
            updateStatus(messageId, DraftStatus.Confirmed)
            append(ChatMessage.Reply(ids.incrementAndGet(), "✓ बिक्री दर्ज हो गई।"))
        }
    }

    fun confirmPayment(messageId: Long, party: String?, amount: Double?) {
        viewModelScope.launch {
            if (party.isNullOrBlank() || amount == null) {
                append(ChatMessage.Reply(ids.incrementAndGet(), "नाम और रकम भरें।"))
                return@launch
            }
            khata.applyRepayment(party, amount, null)
            updateStatus(messageId, DraftStatus.Confirmed)
            append(ChatMessage.Reply(ids.incrementAndGet(), "✓ $party का ₹${amount.toLong()} भुगतान दर्ज।"))
        }
    }

    fun cancel(messageId: Long) = updateStatus(messageId, DraftStatus.Cancelled)

    /** Preload the on-device LLM (intent prefix cache) so the first message is fast. */
    fun warmUpLlm() {
        viewModelScope.launch { runCatching { route.warmUp() } }
    }

    // ---- voice (lifted from SaleEntryViewModel) ----

    fun warmUpVoice() {
        if (!whisper.voiceEnabled) return
        viewModelScope.launch { runCatching { whisper.warmUp() } }
    }

    fun toggleVoice() {
        when (_voice.value) {
            VoiceState.Recording -> recording.set(false)
            VoiceState.Transcribing -> Unit
            else -> startRecording()
        }
    }

    private fun startRecording() {
        if (!whisper.isModelPresent()) {
            _voice.value = VoiceState.Error("Voice model missing — push the ggml model to the phone.")
            return
        }
        recording.set(true)
        _voice.value = VoiceState.Recording
        viewModelScope.launch {
            try {
                val samples = audioRecorder.record(maxSeconds = 12) { recording.get() }
                if (samples.isEmpty()) {
                    _voice.value = VoiceState.Error("सुनाई नहीं दिया — फिर से बोलें")
                    return@launch
                }
                _voice.value = VoiceState.Transcribing
                val text = whisper.transcribe(samples, lang = "hi")
                _transcript.emit(text)
                _voice.value = VoiceState.Idle
            } catch (t: Throwable) {
                Timber.e(t, "voice transcription failed")
                _voice.value = VoiceState.Error(t.message ?: "Voice failed")
            }
        }
    }

    // ---- helpers ----

    private fun append(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun updateStatus(messageId: Long, status: DraftStatus) {
        _messages.value = _messages.value.map { m ->
            when (m) {
                is ChatMessage.SaleDraft -> if (m.id == messageId) m.copy(status = status) else m
                is ChatMessage.PaymentDraft -> if (m.id == messageId) m.copy(status = status) else m
                else -> m
            }
        }
    }

    private fun AssistantResult.toMessage(id: Long): ChatMessage = when (this) {
        is AssistantResult.SaleDraft -> ChatMessage.SaleDraft(id, entries)
        is AssistantResult.PaymentDraft -> ChatMessage.PaymentDraft(id, party, amount)
        is AssistantResult.PnlAnswer -> ChatMessage.PnlAnswer(id, summary)
        is AssistantResult.Reply -> ChatMessage.Reply(id, text)
        AssistantResult.Unavailable -> ChatMessage.Reply(id, "सर्वर बंद है — llama-server चालू करें।")
    }
}

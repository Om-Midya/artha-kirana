package com.artha.kirana.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.voice.AudioRecorder
import com.artha.kirana.data.voice.WhisperEngine
import com.artha.kirana.domain.model.SaleEntry
import com.artha.kirana.domain.usecase.LogSaleUseCase
import com.artha.kirana.domain.usecase.ParseSaleEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
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

/** Voice-entry status, surfaced next to the mic button. */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object Recording : VoiceState
    data object Transcribing : VoiceState
    data class Error(val message: String) : VoiceState
}

@HiltViewModel
class SaleEntryViewModel @Inject constructor(
    private val parseSale: ParseSaleEntryUseCase,
    private val logSale: LogSaleUseCase,
    private val audioRecorder: AudioRecorder,
    private val whisper: WhisperEngine,
) : ViewModel() {

    private val _state = MutableStateFlow<SaleEntryUiState>(SaleEntryUiState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<SaleEntryEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val _voice = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voice = _voice.asStateFlow()

    /** Emitted when a recording is transcribed — the screen drops this into the input field. */
    private val _transcript = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transcript = _transcript.asSharedFlow()

    val voiceEnabled: Boolean get() = whisper.voiceEnabled

    private val recording = AtomicBoolean(false)

    /** Tap to start recording; tap again to stop → transcribe. */
    fun toggleVoice() {
        when (_voice.value) {
            VoiceState.Recording -> recording.set(false) // stop → record() returns, then we transcribe
            VoiceState.Transcribing -> Unit
            else -> startRecording()
        }
    }

    private fun startRecording() {
        if (!whisper.isModelPresent()) {
            _voice.value = VoiceState.Error("Voice model missing — push ggml-tiny.bin to the phone.")
            return
        }
        recording.set(true)
        _voice.value = VoiceState.Recording
        viewModelScope.launch {
            try {
                val samples = audioRecorder.record(maxSeconds = 12) { recording.get() }
                if (samples.isEmpty()) {
                    _voice.value = VoiceState.Idle
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

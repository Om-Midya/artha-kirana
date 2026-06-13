# Artha Kirana — Conversational Assistant Layer (Design Spec)

**Date:** 2026-06-14 · **Status:** Approved (brainstorm) → ready for implementation plan
**Branch baseline:** `main` (Phases 0–2 merged; Phase 4 voice working on-device)
**Related:** `CLAUDE.md` (canonical spec), `HANDOFF.md`, `docs/STATUS.md`, memory `artha-assistant-chat-direction`

---

## 1. Goal

Add a conversational **Assistant** layer so the whole app feels like one assistant: the
shopkeeper types or speaks a natural Hindi/Hinglish utterance, and Artha routes it to the
already-implemented use-cases (log a sale, record a payment, answer a P&L question) and
replies in the thread. This is additive — it layers on top of the §18-stable sale flow and
reuses existing use-cases; it does not replace the existing screens.

## 2. Approved decisions (from brainstorming)

| Decision | Choice |
|---|---|
| **LLM pattern** | Intent **ROUTER**, not an open-ended ReAct/tool-calling agent. Qwen 2.5 3B is reliable at single-step, grammar-constrained routing — not multi-step tool-calling, esp. in Hindi. |
| **Routing architecture** | **Two-stage (Option B): classify → intent-specific extractor.** Each LLM call is short and narrowly grammar-constrained (Qwen 3B's strength); maximises reuse of the proven `SaleParser`; per-stage debuggable. |
| **Interaction model** | Persistent **chat thread** (user + assistant bubbles), not a one-shot command bar. Confirm cards render inline as messages. |
| **Mutation safety** | **Inline confirm card** — writes (`log_sale`, `record_payment`, …) render an editable confirm card; nothing is written until the user taps **Confirm**. Reuses the existing Sale Entry confirm-card pattern. |
| **Conversation memory** | **Stateless per-message.** Each utterance is routed independently; no prior turns fed to the LLM. Thread is visual history only. Follow-ups must be self-contained. |
| **Placement** | **"Assistant" bottom-nav tab at the CENTER**, raised/protruding circular FAB-style button (brand-gold). 5 slots: Home · Inventory · **[Assistant]** · Khata · P&L. |
| **Voice** | **Voice input only.** Mic in the chat input reuses the proven whisper pipeline. Replies are text-only for now (Hindi TTS deferred to Phase 4 proper). |
| **Scope** | **Thin slice first: 3 intents** — `log_sale`, `query_pnl`, `record_payment`. Then expand. |

## 3. Architecture

All new components are **additive**. The only modified existing file is `ui/ArthaApp.kt`
(nav). The §18-stable sale path is untouched — the Assistant is a *new caller* of the same
use-cases. The existing Sale Entry screen remains as-is (a fallback / alternate entry).

### 3.1 New components

| Component | Layer | Responsibility |
|---|---|---|
| `AssistantIntent` (enum) | `domain/model` | `LOG_SALE, RECORD_PAYMENT, QUERY_PNL, UNKNOWN` (thin slice; more added later) |
| `AssistantResult` (sealed) | `domain/model` | Router output: `SaleDraft(entries)`, `PaymentDraft(party, amount)`, `PnlAnswer(summary, period)`, `Reply(text)`, `Unavailable` |
| `IntentRouter` | `data/llm` | **Call 1**: `client.chat(INTENT_SYSTEM_PROMPT, text, INTENT_RESPONSE_FORMAT)` → enum-only `json_schema` → `AssistantIntent`. Holds prompt + grammar as single source of truth (mirrors `LlmEngine`). |
| `PaymentParser` | `data/llm` | Parses `{party, amount}` JSON → typed (mirrors `SaleParser`; never throws → returns null on failure) |
| `RouteAssistantUseCase` | `domain/usecase` | The orchestrator (§3.2) |
| `AssistantScreen` / `AssistantViewModel` | `ui/assistant` | Chat thread UI + state |
| `ChatMessage` (sealed) + message composables | `ui/assistant` | Thread model + bubbles + inline draft cards |

### 3.2 Orchestrator — `RouteAssistantUseCase(text): AssistantResult`

1. `HindiNumbers.normalize(text)` (reuse — number-words → digits).
2. `IntentRouter.classify(normalized)` → `AssistantIntent` (**Call 1, always**).
3. Dispatch on intent:
   - `LOG_SALE` → reuse **`ParseSaleEntryUseCase`** verbatim (**Call 2**) → `SaleDraft(entries)`; if entries empty → `Reply("समझ नहीं आया …")`.
   - `RECORD_PAYMENT` → `PaymentParser` via a small `{party, amount}` extractor grammar (**Call 2**) → `PaymentDraft`.
   - `QUERY_PNL` → detect period by **Kotlin keyword match** (`आज`→TODAY, `हफ्ता`/`सप्ताह`/`week`→WEEK, `महीना`/`month`→MONTH, default TODAY) — **no Call 2** → `GetPnlSummaryUseCase(period).first()` → `PnlAnswer`.
   - `UNKNOWN` → `Reply(...)`.
4. Any `LlmUnavailableException` (from either call) → `AssistantResult.Unavailable` (reuses the existing `Result.failure` path).

### 3.3 Grammars (json_schema → GBNF on llama-server, proven on b9620)

- **Call 1 (intent):** `{"intent": <enum log_sale|record_payment|query_pnl|unknown>}`, `required: [intent]`. Built like `LlmEngine.SALE_RESPONSE_FORMAT`. System prompt = short rules + a few Hindi few-shot examples (e.g. `रमेश ने पचास दिए → record_payment`; `आज की कमाई → query_pnl`; `दो किलो चावल अस्सी का → log_sale`).
- **Call 2a (sale):** existing `LlmEngine.SALE_RESPONSE_FORMAT` + `SALE_SYSTEM_PROMPT`, unchanged.
- **Call 2b (payment):** `{"party": string|null, "amount": number|null}`, both required. Small system prompt with Hindi examples.

### 3.4 Reused untouched

`LogSaleUseCase`, `ParseSaleEntryUseCase`, `GetPnlSummaryUseCase`, `KhataRepository.applyRepayment`,
`HindiNumbers`, `EditableEntryCard`, `LlmHttpClient.chat(system, user, responseFormat)`,
`AudioRecorder`, `WhisperEngine`, the `VoiceState` pattern.

## 4. UI / Navigation

### 4.1 Bottom nav with protruding center

Material3's `NavigationBar` cannot render a raised center item. Build a small custom bar:
4 `NavigationBarItem`s — Home, Inventory, **[center gap]**, Khata, P&L — with a brand-gold
`FloatingActionButton` docked/overlapping the center (via `Scaffold` center-docked FAB or a
`Box` overlaying the bar). Tapping it navigates to the `assistant` route. `TopDest` grows to
include Assistant so selection/back-stack logic stays consistent. **Only change to `ArthaApp.kt`.**

### 4.2 Chat thread

```kotlin
// ui/assistant/ChatMessage.kt
sealed interface ChatMessage {
    val id: Long
    data class User(override val id: Long, val text: String) : ChatMessage
    data class Reply(override val id: Long, val text: String) : ChatMessage          // query answers, acks, errors
    data class SaleDraft(override val id: Long, val entries: List<SaleEntry>, val status: DraftStatus) : ChatMessage
    data class PaymentDraft(override val id: Long, val party: String?, val amount: Double?, val status: DraftStatus) : ChatMessage
    data class PnlAnswer(override val id: Long, val summary: PnlSummary) : ChatMessage
}
enum class DraftStatus { Pending, Confirmed, Cancelled }
```

`AssistantViewModel` exposes `messages: StateFlow<List<ChatMessage>>`, `inputState (Idle/Routing)`,
and reuses the **same `VoiceState` pattern** as `SaleEntryViewModel`. UI = `LazyColumn`
auto-scrolling to bottom; user bubbles right-aligned, assistant left. Drafts reuse
`EditableEntryCard` inside a bubble with **Cancel / Confirm**.

**Send flow:** `send(text)` → append `User` → `inputState=Routing` → `RouteAssistantUseCase`
→ append the resulting `ChatMessage`. On **Confirm** of a draft → call `LogSaleUseCase` /
`applyRepayment` → flip that card's status to `Confirmed` → append a Hindi ack `Reply`.
Queries render their answer card immediately (read-only, no confirm).

### 4.3 Voice (input only)

Lift the proven mic logic (`AudioRecorder` + `WhisperEngine` + `toggleVoice()` /
`warmUpVoice()` / transcript flow) into `AssistantViewModel` (same path: record → whisper
`hi` → text). The transcript **fills the chat input** so the user can glance/edit before
tapping send (matches existing transcript-into-field behavior). No TTS.

## 5. Error / empty states

Everything renders as a `Reply` bubble — never a crash, never an empty screen.

- LLM offline (`Unavailable`) → "सर्वर बंद है — llama-server चालू करें।"
- `UNKNOWN` / empty parse → "समझ नहीं आया — दोबारा कहें (जैसे: 'दो किलो चावल अस्सी का')।"
- `record_payment` with no party → confirm card shows with an empty, editable party field.
- Empty thread → greeting + 3 example chips ("दो किलो चावल ₹80", "रमेश ने ₹50 दिए", "आज की कमाई?").

## 6. Testing

TDD the pure logic; build-verify + on-device checkpoint for UI/LLM wiring (per HANDOFF,
adb-driving Compose is flaky — the human drives UI verification).

- **Unit:** `PaymentParser.parse()` (JSON → typed; mirrors existing `SaleParser` tests);
  period keyword detection (pure fn); `RouteAssistantUseCase` dispatch (fake `IntentRouter` /
  engine → assert each intent yields the right `AssistantResult`; assert `Unavailable` on failure).
- **Prompt validation:** add `scripts/validate-intent-prompt.py` (mirrors
  `scripts/validate-sale-prompt.py`) — sends Hindi utterances over HTTP to the live server,
  asserts the classified intent. Keep its prompt synced with `IntentRouter.INTENT_SYSTEM_PROMPT`
  (same sync discipline as the sale prompt).
- **UI/LLM wiring:** `./gradlew :app:installDebug` build-verify, then on-device checkpoint
  with the human driving.
- Commit per task.

## 7. Thin-slice exit criteria

On the iQOO, from the Assistant tab:
1. Type/speak a Hindi sale → confirm card → Confirm → ledger updates (Home/P&L reflect it).
2. "रमेश ने ₹50 दिए" → payment card → Confirm → Ramesh's khata balance drops.
3. "आज की कमाई?" → P&L answer bubble with today's revenue/profit.

Then expand intents: `add_item`, `restock`, `query_khata`, `query_low_stock`, `market_insights`.

## 8. Constraints / gotchas (carried from HANDOFF)

- Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3. Do not upgrade.
- llama-server must run **on the iQOO** (`CLAUDE.md` §1). Validate prompt changes with
  `adb forward tcp:8080 tcp:8080` + the validate script.
- Keep `IntentRouter.INTENT_SYSTEM_PROMPT` synced with `validate-intent-prompt.py`
  (same rule as `SALE_SYSTEM_PROMPT` ↔ `validate-sale-prompt.py`).
- Don't touch Phase 3 (OCR/CameraX/`BillParser`/`BillScanScreen`) — a collaborator owns it.

## 9. Out of scope (this slice)

Context-carrying multi-turn; optimistic+Undo; TTS spoken replies; the non-thin-slice intents
(added after the slice proves out); anything in `CLAUDE.md` §17.

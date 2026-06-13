# Artha Kirana — Status

**Updated:** 2026-06-14 · **Branch:** `main` (Phase 0–2 merged); Assistant slice on `feat/assistant-layer` · **Device:** iQOO 15 (`10BFBG0CEL001DB`)

## Progress

| Phase | Status |
|---|---|
| **Phase 0 — Foundation** | ✅ Done & device-verified |
| **Phase 1 — Core sale loop** | ✅ Done & device-verified (incl. Task 1.7 §18 = 5/5) |
| **Phase 2 — Inventory / Khata / P&L** | ✅ Done & merged to `main` (13/13 tasks, final review clean, unit tests green). Device-verified screens + record-payment + worker pipeline. Owner-verifying two §15 visuals (low-stock notification fire/clear; LLM-sale decrement/COGS). |
| Phase 3 — Bill scanning (OCR) | 🤝 In progress by a collaborator — **not this agent's scope** (do not touch ML Kit/CameraX/BillParser). |
| **Phase 4 — Voice + vernacular** | 🟢 Voice pipeline WORKING on-device. whisper.cpp v1.7.4 (arm64, `-O3`) + fine-tuned **whisper-hindi-small q5_1** → accurate Devanagari → `HindiNumbers` normalizer (number-words→digits) → Qwen with `json_schema` grammar → confirm card. ~2-3s. Hallucination on poor audio curbed (single_segment off + RMS silence gate). **Still TODO:** Hindi TTS "Hear summary", recording animation, vernacular toggle, `inputMethod="voice"`, Devanagari party-name vs romanized. |
| Phase 5 — Market insights (Claude API) | ⬜ Not started (needs API key) |
| Phase 6 — Demo hardening | ⬜ Not started |
| **Assistant layer (thin slice)** | 🟢 Built on branch `feat/assistant-layer` (14/14 tasks, per-task spec+quality reviews, unit tests green, full `assembleDebug` clean, installed on iQOO). Conversational chat-thread tab (center protruding gold FAB) → stateless two-stage intent router (classify → intent-specific extractor) over existing use-cases. 3 intents: `log_sale`, `query_pnl`, `record_payment`. **Live intent classifier = 10/10 on-device** (`scripts/validate-intent-prompt.py` vs Qwen 3B). Inline confirm cards reuse `EditableEntryCard`; voice input reuses the whisper mic; replies text-only (TTS deferred). **Awaiting human on-device UI walkthrough** (3 flows + voice + offline) before merge. Spec: `docs/superpowers/specs/2026-06-14-artha-assistant-design.md`; plan: `docs/superpowers/plans/2026-06-14-artha-assistant.md`. |

## What works (verified on the iQOO)

- Builds & installs (AGP 8.13 / Gradle 8.13 / JDK 17). 43 Kotlin source files, 5 test files, all unit tests green.
- 4-tab nav shell (Home / Inventory / Khata / P&L); Hilt DI graph valid at runtime.
- **Full sale loop:** type a Hindi/Hinglish sale → on-device LLM (llama-server, Qwen 2.5 3B over `127.0.0.1:8080`) parses → editable confirmation card → Room save → Home updates reactively. Demonstrated live: `sugar / Cash sale / ₹50` and `chawal / Credit · Ramesh / ₹80`.
- Revenue (today) aggregates correctly and excludes repayments.
- **§18 parser cases: 5/5**, stable across 3 runs (see `docs/demo-runbook.md`).

### Phase 2 — verified on the iQOO (2026-06-13)
- **Inventory:** empty-state + FAB; add/edit/restock bottom sheet renders and saves; low-stock rows highlight (`reorderThreshold > 0 && qtyInStock < reorderThreshold`).
- **Khata:** total-outstanding card + party list (balance red=owes us); party detail shows balance + transaction history; **Record payment verified end-to-end** — a ₹30 repayment dropped Ramesh ₹80→₹50, history shows the green Repayment row, reactively.
- **P&L:** Today/Week/Month tabs; metric cards arithmetic verified (Gross profit ₹170 = Revenue ₹170 − COGS ₹0; Outstanding ₹50; Cash ₹90); Vico 2.1.3 column chart renders 7-day revenue (x-axis shows day indices 0–6, not weekday labels — minor polish item).
- **Low-stock worker:** `InventoryAlertWorker` (`@HiltWorker`) fires and returns SUCCESS via `HiltWorkerFactory` (logcat-confirmed); WorkManager Hilt `Configuration.Provider` wired, default initializer removed, no double-init crash. Low-stock detection works on-device (`soap` shows "· low!" via the same predicate the worker's `lowStock()` query uses). **The visible notification fire + auto-clear-on-restock is owner-verified separately:** force-running the *periodic* job via `adb cmd jobscheduler run` is unreliable on OriginOS (job id rotates per cycle; WM logs stripped). For a deterministic demo/verify trigger, add a debug-only expedited `OneTimeWorkRequest` of the same worker (Phase 6 hardening). Notify/cancel code is trivial + verbatim from the plan.
- **Manual §15 checks owned by the user:** (A) log an LLM sale of a stocked item → verify inventory decrement + non-zero COGS; (B) low-stock notification fire + auto-clear on restock.
- All unit tests green (TimeRange, RevenueBucketing, GetPnlSummaryUseCase + Phase 1 suite).

### Phase 4 voice — SPIKE B status (2026-06-13)
- **Native build PASSES** (Mac, no device): `./gradlew :app:assembleDebug` compiles vendored whisper.cpp v1.7.4 (`app/src/main/cpp/whisper/`) + `jni.c` → `libwhisper_android.so` (+ libwhisper/libggml*) for arm64-v8a, packaged in the APK. See `app/src/main/cpp/README.md`.
- **Wired:** `WhisperLib` (JNI) → `WhisperContext` → `WhisperEngine` (lazy singleton, loads `/sdcard/Download/ggml-tiny.bin`) + `AudioRecorder` (16 kHz mono → float). Mic button in Sale Entry: record → transcribe `hi` → drops text into the input → existing Parse path.
- **SPIKE B PASSED + device-verified.** Final model = **fine-tuned `whisper-hindi-small` → ggml q5_1** at `/sdcard/Android/data/com.artha.kirana/files/` (app external-files dir — scoped storage blocks /sdcard/Download for the app process). Critical perf fix: force `-O3` in CMake (debug installs were `-O0` → ~30x slow). UTF-8 JNI fix (return bytes, decode in Kotlin). Hindi parsing hardened: `HindiNumbers` normalizer + `json_schema` grammar + 500-retry. Hallucination curbed (single_segment off + RMS gate).
- **Remaining Phase 4:** `TextToSpeechManager` (Hindi TTS "Hear summary"), recording animation, vernacular toggle, mark voice entries `inputMethod="voice"`, decide Devanagari-vs-romanized party names.

## Spikes

- **SPIKE A** (LLM connectivity) ✅ · **SPIKE C** (OriginOS battery whitelist) ✅
- **SPIKE B** (whisper.cpp on-device ASR) ⏳ — per the updated `CLAUDE.md` §15, voice is now **whisper.cpp via JNI**, not Android SpeechRecognizer. Spike = build whisper.cpp for Android (NDK/CMake) + transcribe a Hindi clip offline. Gates Phase 4. Fallback: SpeechRecognizer `EXTRA_PREFER_OFFLINE`, or typed-only.

## Key decisions / deviations

- **LLM = localhost HTTP** to `llama-server` (Qwen 2.5 3B) on `127.0.0.1:8080`, not in-app LiteRT/JNI. The server runs **on the phone** (the app hits phone-loopback — no Mac/tether at runtime). **`CLAUDE.md` §1 hard requirement: the server must run ON the iQOO, never on a tethered Mac.** Today it's *started* via an `adb shell` command, but the process is phone-side and survives unplugging. The **Phase 6 airplane-mode gate** (unplug, confirm a sale still parses) proves this; for a tether-free *start*, use Termux or a phone-side launcher. `adb forward` is used only by the Mac-side prompt-validation harness, never the app.
- **AGP 9.0.1 → 8.13.0** (AGP 9 breaks Hilt/KSP/Compose).
- **SQLCipher deferred** behind a swap-in seam in `DatabaseModule`.
- **Vico (charts) deferred to Phase 2** — 3.1.0 needs Kotlin 2.3.x; pick a Kotlin-2.0-compatible version.
- **Room DB is version 2** (`itemName` denormalized onto sales; `fallbackToDestructiveMigration`).

## Known issues / follow-ups

- **On-device-server gate (Phase 6, CLAUDE.md §1/§6):** prove a sale parses with airplane mode ON and the Mac unplugged. Provide a tether-free start (Termux / phone-side launcher) — the `adb shell` start is fine for dev but the demo must not depend on a laptop.
- Model occasionally varies on `type` under sampling (temp 0.1); §18 currently 5/5 but watch edge cases.
- `validate-sale-prompt.py`'s `SYSTEM_PROMPT` must be kept in sync with `LlmEngine.SALE_SYSTEM_PROMPT`.
- SQLCipher not enabled (deferred).

## Docs map

- `HANDOFF.md` — start here (pickup guide).
- `docs/superpowers/specs/2026-06-13-artha-llm-http-architecture-design.md` — architecture (delta over `CLAUDE.md`).
- `docs/superpowers/plans/2026-06-13-artha-phase0-phase1.md` — Phase 0/1 plan + Phase 2–6 roadmap.
- `docs/demo-runbook.md` — start-server, battery, §18 results, demo script.
- `CLAUDE.md` — canonical full spec.

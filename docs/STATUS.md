# Artha Kirana — Status

**Updated:** 2026-06-13 · **Branch:** `feat/phase0-foundation` (off `main`) · **Device:** iQOO 15 (`10BFBG0CEL001DB`)

## Progress

| Phase | Status |
|---|---|
| **Phase 0 — Foundation** | ✅ Done & device-verified |
| **Phase 1 — Core sale loop** | ✅ Done & device-verified (incl. Task 1.7 §18 = 5/5) |
| **Phase 2 — Inventory / Khata / P&L** | 🟡 Code complete (13/13 tasks, all unit tests green); device-verified Inventory/Khata/P&L screens + record-payment + worker pipeline. **Pending manual §15 walkthrough:** low-stock notification visual + LLM-sale-driven decrement/COGS. |
| Phase 3 — Bill scanning | ⬜ Not started |
| Phase 4 — Voice + vernacular | ⬜ Not started (gated on SPIKE B) |
| Phase 5 — Market insights (Claude API) | ⬜ Not started (needs API key) |
| Phase 6 — Demo hardening | ⬜ Not started |

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
- **Low-stock worker:** `InventoryAlertWorker` (`@HiltWorker`) fires and returns SUCCESS via `HiltWorkerFactory` (logcat-confirmed); WorkManager Hilt `Configuration.Provider` wired, default initializer removed, no double-init crash. **The visible notification fire + auto-clear-on-restock has NOT yet been eyeballed — manual step.**
- All unit tests green (TimeRange, RevenueBucketing, GetPnlSummaryUseCase + Phase 1 suite).

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

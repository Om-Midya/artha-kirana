# Artha Kirana — Status

**Updated:** 2026-06-13 · **Branch:** `feat/phase0-foundation` (off `main`) · **Device:** iQOO 15 (`10BFBG0CEL001DB`)

## Progress

| Phase | Status |
|---|---|
| **Phase 0 — Foundation** | ✅ Done & device-verified |
| **Phase 1 — Core sale loop** | ✅ Done & device-verified (incl. Task 1.7 §18 = 5/5) |
| Phase 2 — Inventory / Khata / P&L | ⬜ Not started |
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

## Spikes

- **SPIKE A** (LLM connectivity) ✅ · **SPIKE C** (OriginOS battery whitelist) ✅
- **SPIKE B** (Hindi offline STT) ⏳ **needs the user** — must speak into the phone in airplane mode; run before Phase 4. Until then `VOICE_ENABLED=true` (optimistic default).

## Key decisions / deviations

- **LLM = localhost HTTP** to `llama-server`, not in-app LiteRT/JNI (design doc). App is not self-contained — server started via ADB (tethered-demo).
- **AGP 9.0.1 → 8.13.0** (AGP 9 breaks Hilt/KSP/Compose).
- **SQLCipher deferred** behind a swap-in seam in `DatabaseModule`.
- **Vico (charts) deferred to Phase 2** — 3.1.0 needs Kotlin 2.3.x; pick a Kotlin-2.0-compatible version.
- **Room DB is version 2** (`itemName` denormalized onto sales; `fallbackToDestructiveMigration`).

## Known issues / follow-ups

- Model occasionally varies on `type` under sampling (temp 0.1); §18 currently 5/5 but watch edge cases.
- `validate-sale-prompt.py`'s `SYSTEM_PROMPT` must be kept in sync with `LlmEngine.SALE_SYSTEM_PROMPT`.
- SQLCipher not enabled (deferred).

## Docs map

- `HANDOFF.md` — start here (pickup guide).
- `docs/superpowers/specs/2026-06-13-artha-llm-http-architecture-design.md` — architecture (delta over `CLAUDE-1.md`).
- `docs/superpowers/plans/2026-06-13-artha-phase0-phase1.md` — Phase 0/1 plan + Phase 2–6 roadmap.
- `docs/demo-runbook.md` — start-server, battery, §18 results, demo script.
- `CLAUDE-1.md` — canonical full spec.

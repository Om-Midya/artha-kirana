# Artha Kirana — LLM-over-HTTP Architecture Design

**Date:** 2026-06-13
**Status:** Approved (design); plan-only session, no app code written yet
**Canonical spec:** `CLAUDE-1.md` (project root) — this doc is a **delta** over that spec, not a replacement.

---

## 0. Purpose of this document

`CLAUDE-1.md` is the full 18-section build spec for Artha Kirana. It assumes the on-device
LLM runs **in-process** via LiteRT-LM / Gemma 3 1B. This session changed that decision. This
document records:

1. The chosen LLM architecture (localhost HTTP → `llama-server`) and everything it changes.
2. The handful of other decisions made this session (SQLCipher deferral, model delivery, spikes).
3. Everything in `CLAUDE-1.md` that **still stands unchanged**.

Where this document and `CLAUDE-1.md` disagree, **this document wins**. Everything else: follow `CLAUDE-1.md`.

---

## 1. Context & prior work

- **Device:** iQOO 15 — Snapdragon 8 Elite Gen 5, 16GB RAM, Android 16, arm64-v8a, 1440×3168.
- **Proven groundwork (handoff):** llama.cpp **b9620** on the phone at `/data/local/tmp/llama/llama-b9620/`
  serving **Qwen 2.5 3B Instruct Q5_K_M** (`/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf`, 2.4GB)
  at ~62 tok/s prompt eval, ~17 tok/s generation. `llama-server` (OpenAI-compatible) is in the same tarball.
- **Current app state:** default Android Studio Compose template only (`MainActivity` + theme).
  AGP 9.0.1, Kotlin 2.0.21, Compose BOM 2024.09, minSdk 24, targetSdk 36, namespace `com.artha.kirana`.

---

## 2. Decision: LLM access via localhost HTTP

**The app talks to the on-device LLM by HTTP POST to `http://127.0.0.1:8080`**, where `llama-server`
serves Qwen 2.5 3B on the phone.

**Chosen over:**
- LiteRT-LM / MediaPipe + Gemma 3 1B (the spec's path) — new integration risk; Gemma 1B weaker on Hindi.
- Embedding llama.cpp via JNI — self-contained but native-build/bundling cost.

**Rationale:** reuses 100% of the proven handoff setup; collapses the riskiest part of the spec into a
plain HTTP call; fastest path to a working demo.

**Accepted tradeoff:** the app is **not self-contained** — `llama-server` must be started via ADB
before the demo. This is acceptable for a tethered hackathon demo. **Embed-via-JNI is the documented
post-hackathon upgrade path.**

### 2.1 Architecture

```
SaleEntryScreen / BillScanScreen
        │
        ▼
ParseSaleEntryUseCase / ScanBillUseCase
        │
        ▼
LlmEngine ──(build prompt)──► LlmHttpClient ──HTTP POST──► 127.0.0.1:8080  (llama-server, Qwen 3B)
        │                                                        on the phone
        ▼
JsonParser.extractJson()  ──► domain model  (or manual-entry fallback on failure)
```

- **`LlmEngine`** = orchestration only (build prompt from `CLAUDE-1.md` §5 → call client → extract JSON).
- **`LlmHttpClient`** = Ktor client. `POST /v1/chat/completions` (OpenAI-compatible, confirmed in handoff),
  `temperature=0.1`, `max_tokens=256`, stop sequences `["```", "\n\n"]`.
- **Symmetry:** the local LLM and the cloud Claude API (market trends) are **both Ktor clients** — one
  networking pattern, two base URLs.

### 2.2 Required platform plumbing

- **`res/xml/network_security_config.xml`** allowing **cleartext to `127.0.0.1` only** (Android 9+ blocks
  cleartext by default). Reference it from `AndroidManifest.xml` `application` tag.
- **No `adb forward`** needed — the app calls localhost *on the phone itself*.
- **`scripts/start-llama-server.sh`** in the repo (mirrors the handoff `llama-server` invocation):
  ```bash
  adb shell "cd /data/local/tmp/llama/llama-b9620 && LD_LIBRARY_PATH=. ./llama-server \
    -m /sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf --host 0.0.0.0 --port 8080 &"
  ```
- **Demo runbook step 0:** "start llama-server" — explicit, not a demo-day surprise.

### 2.3 Health & graceful degradation (replaces SPIKE A)

`LlmEngine` pings `GET /health` on app start. If the server is unreachable, the app surfaces
"LLM offline — start the server" and **every parse falls back to the manual-entry form** (never crashes).

---

## 3. Other decisions this session

### 3.1 SQLCipher — deferred
Build Room **without** SQLCipher first, behind a `DatabaseModule` whose `Room.databaseBuilder` is trivial
to swap to an encrypted `SupportFactory` later. SQLCipher adds real setup friction (native lib, passphrase,
keystore) for zero demo-visible benefit in a 30-hour build. **This is an explicit, reversible seam, not a
silent drop.**

### 3.2 Model delivery — ADB push
Push the GGUF to `/sdcard` before the demo. The Qwen model is already at
`/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf`. No first-launch download code.

### 3.3 Session scope — plan only
This session produces the design doc + implementation plan. **No app code is written.**

---

## 4. Package structure (delta over `CLAUDE-1.md` §3)

Identical to the spec **except** the LLM area, which becomes HTTP-shaped:

```
data/
├── remote/
│   ├── LlmHttpClient.kt      # Ktor → 127.0.0.1:8080 (local Qwen)
│   ├── ClaudeApiClient.kt    # Ktor → api.anthropic.com (cloud market trends)
│   └── dto/                  # @Serializable request/response DTOs for both
├── llm/
│   ├── LlmEngine.kt          # orchestrates: prompt → LlmHttpClient → JsonParser
│   ├── SaleParser.kt         # sale-text → SaleEntry        (system prompt §5)
│   └── BillParser.kt         # OCR-text → PurchaseEntries   (system prompt §5)
```

**Unchanged from spec:** `db/`, `repository/`, `domain/`, all `ui/` screens, `worker/`, `voice/`,
`ocr/`, `util/`. DB schema (§4), LLM prompts (§5), JSON-extraction safety (§6), P&L computation (§10),
coding standards (§16), out-of-scope list (§17), LLM test cases (§18) — all stand verbatim.

---

## 5. Phases (delta over `CLAUDE-1.md` §15)

- **Phase 0 — Foundation.** Gradle deps (Hilt, Room+KSP, Ktor, kotlinx.serialization, Nav, Compose),
  Hilt setup, Room (**no SQLCipher**) with 5 entities + DAOs, repositories (stubs), Navigation 4-tab,
  brand theme. **Plus:** `network_security_config.xml`, `scripts/start-llama-server.sh`, and the 3 spikes below.
- **Phase 1 — Core sale loop.** `LlmEngine` (HTTP) → `JsonParser` → `ParseSaleEntryUseCase` →
  `LogSaleUseCase` → `SaleEntryScreen` + `HomeScreen`. Validate against §18 test cases (>90% parse).
- **Phases 2–6** — Inventory/Khata/P&L, Bill scan, Voice+TTS, Claude insights, Demo hardening — per spec §15.

### 5.1 Spikes (rewritten for this architecture)
- **SPIKE A (LLM)** → **connectivity check**: app does `GET /health` + one `POST /completion` to
  localhost `llama-server`, logs round-trip ms. Pass = JSON back in <5s. (Throughput already proven: ~17 tok/s.)
- **SPIKE B (Hindi offline STT)** → **unchanged**. Highest-uncertainty item; test on real iQOO in airplane
  mode in first 2h. Fail → `VOICE_ENABLED=false`, typed entry covers the demo.
- **SPIKE C (OriginOS battery)** → **unchanged**. ADB script + in-app "fix battery" deep link.

### 5.2 Context7 verification timing (per spec §0 "research before you build")
Verify each library's current API in the phase it first appears:
- **Phase 0/1:** Ktor client (Android engine) + kotlinx.serialization, Room + KSP, Hilt, Navigation Compose.
- **Phase 2:** Vico (charts), WorkManager.
- **Phase 3:** CameraX (Compose) + ML Kit Text Recognition.

---

## 6. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `llama-server` not running at demo time → every parse fails | `/health` check on app start; manual-entry fallback on every parse; "start server" runbook step 0 |
| Hindi offline STT unsupported on OriginOS | SPIKE B in first 2h; `VOICE_ENABLED=false` fallback to typed entry |
| OriginOS kills WorkManager (battery) | SPIKE C ADB script + in-app "fix battery" deep link |
| localhost cleartext blocked on Android 9+ | `network_security_config.xml` permitting cleartext to `127.0.0.1` only |
| LLM returns non-JSON despite prompt | `JsonParser.extractJson` (§6) + try-catch → manual form |
| Tethered-server dependency undercuts "self-contained app" story | Frame honestly: "inference runs on the phone via a local server"; JNI-embed is the post-hackathon path |

---

## 7. Net effect

The spec stands almost entirely as-is. The only structural changes are: (1) swap the in-app LLM runtime
for a localhost HTTP client, (2) defer SQLCipher behind a swap-in seam, (3) rewrite SPIKE A as a
connectivity check. Everything else in `CLAUDE-1.md` is the build contract.

# Handoff — Artha Kirana

Offline-first AI assistant for kirana shop owners. Speak/type a sale in Hindi → on-device LLM parses it → Room ledger/inventory/khata → vernacular summary. Built for the iQOO 15 (Snapdragon 8 Elite). Full spec: `CLAUDE-1.md`. Current state: `docs/STATUS.md`.

## TL;DR — get running (~2 min)

```bash
cd /Users/archismanmidya/AndroidStudioProjects/Artha
adb devices                          # confirm iQOO 15 (10BFBG0CEL001DB)
./scripts/start-llama-server.sh      # start Qwen 2.5 3B on the phone (127.0.0.1:8080)
./gradlew :app:installDebug          # build + install (JDK 17, AGP 8.13)
adb shell am start -n com.artha.kirana/.MainActivity
```

Then tap **+ New Sale**, type e.g. `2 kilo sugar fifty rupees`, **Parse** → confirm. Home updates.

## Architecture in one paragraph

Kotlin + Compose + Material3, Clean Arch (`data`/`domain`/`ui`), MVVM + StateFlow, single-Activity Nav, Hilt, Room. **The LLM is not in-app** — `LlmEngine` → `LlmHttpClient` (Ktor) POSTs to `llama-server` (llama.cpp, Qwen 2.5 3B) on `http://127.0.0.1:8080/v1/chat/completions` (OpenAI-compatible). The cloud Claude API (Phase 5) reuses the same Ktor `NetworkModule`. `network_security_config.xml` permits cleartext to loopback only.

## State

- ✅ **Phase 0** (foundation + SPIKE A/C) and **Phase 1** (full sale loop, §18 = 5/5) — done, device-verified.
- ⏳ **SPIKE B** (Hindi offline STT) — needs the user to speak in airplane mode; do before Phase 4.
- ⬜ **Next: Phase 2** (Inventory / Khata / P&L). Plan + roadmap: `docs/superpowers/plans/2026-06-13-artha-phase0-phase1.md`. Use a **Kotlin-2.0-compatible Vico** (NOT 3.1.0 — needs Kotlin 2.3).
- Work on branch `feat/phase0-foundation` (9 commits off `main`).

## Gotchas (read before changing things)

- **Toolchain is pinned to AGP 8.13 / Gradle 8.13 on purpose.** AGP 9 breaks Hilt + KSP + Compose. Don't "upgrade" it.
- **llama-server must be running** for any parse. If it's down the app degrades gracefully (manual-entry fallback). Logs: `adb shell tail -f /sdcard/Download/llama-server.log`. Stop: `adb shell "pkill -f llama-server"`.
- **Use `llama-completion`/`llama-server`, never `llama-cli`** (b9620 `llama-cli` hangs over adb). Binaries are thin wrappers — `LD_LIBRARY_PATH=.` is mandatory (handled by the start script).
- **Validate prompt changes** with `scripts/validate-sale-prompt.py` (needs `adb forward tcp:8080 tcp:8080`). It sends Devanagari over HTTP — `adb input text` can't. Keep its `SYSTEM_PROMPT` in sync with `LlmEngine.SALE_SYSTEM_PROMPT`.
- **Room is at version 2** with `fallbackToDestructiveMigration` — schema changes wipe dev data.
- **Driving Compose UI via adb:** uiautomator dumps are single-line and go stale after layout changes (keyboard show/hide); find element bounds by region-grep, re-dump after each layout change.

## Where things live

| Path | What |
|---|---|
| `app/src/main/java/com/artha/kirana/data/remote/` | `LlmHttpClient`, `ClaudeApiClient` (Phase 5), DTOs, `NetworkModule` |
| `…/data/llm/` | `LlmEngine` (system prompt), `SaleParser` |
| `…/data/db/` | Room entities, DAOs, `ArthaDatabase` |
| `…/domain/` | repositories (interfaces), use-cases, models |
| `…/ui/` | `ArthaApp` (nav), `home/`, `entry/`, theme |
| `scripts/` | `start-llama-server.sh`, `validate-sale-prompt.py` |
| `docs/` | `STATUS.md`, `demo-runbook.md`, `superpowers/specs`, `superpowers/plans` |

## Device reference

iQOO 15, serial `10BFBG0CEL001DB`, Android 16, arm64-v8a. ADB on PATH. llama.cpp b9620 at `/data/local/tmp/llama/llama-b9620/`; model at `/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf` (2.4GB). Full on-device-LLM setup notes: `~/Desktop/CrazyStuff/iqoo-mobile/HANDOFF.md`.

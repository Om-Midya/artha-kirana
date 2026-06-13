# Handoff — Artha Kirana

Offline-first AI assistant for kirana shop owners. Speak/type a sale in Hindi → on-device LLM parses it → Room ledger/inventory/khata → vernacular summary. Built for the iQOO 15 (Snapdragon 8 Elite). Full spec: `CLAUDE.md`. Live state: `docs/STATUS.md`.

## Get running (~2 min)

```bash
cd /Users/archismanmidya/AndroidStudioProjects/Artha
adb devices                          # iQOO 15 = 10BFBG0CEL001DB
./scripts/start-llama-server.sh      # Qwen 2.5 3B on the phone (127.0.0.1:8080)
./gradlew :app:installDebug          # JDK 17, AGP 8.13 (pinned)
adb shell am start -n com.artha.kirana/.MainActivity
```

## State (branch `main`)

- ✅ **Phase 0/1/2 done & merged.** Sale loop (type Hindi → on-device parse → confirm → Room → reactive Home, §18 = 5/5). Inventory (add/edit/restock + low-stock highlight), Khata (party list/detail + record-payment), P&L (today/week/month tabs + Vico chart), low-stock `InventoryAlertWorker`. Final review clean; unit tests green.
- 🤝 **Phase 3 (OCR/bill scanning) — a collaborator owns this.** Don't touch ML Kit / CameraX / `BillParser` / `BillScanScreen`.
- 🟢 **Phase 4 (voice) — working on-device.** whisper.cpp (vendored `app/src/main/cpp/whisper/`, arm64, **`-O3` forced in CMake** — debug installs default to `-O0` = ~30x slower) + **fine-tuned `whisper-hindi-small` ggml q5_1** (accurate Hindi). Model lives in the app external-files dir (`adb push … /sdcard/Android/data/com.artha.kirana/files/ggml-hindi-small-q5_1.bin`; **NOT** /sdcard/Download — scoped storage blocks the app from reading it). Mic in Sale Entry: record → whisper `hi` → `HindiNumbers.normalize` (number-words→digits) → Qwen w/ `json_schema` grammar → confirm. Conversion recipe + model cache: see `docs/STATUS.md` + memory. TODO: Hindi TTS, animation, vernacular toggle.
- ⬜ Phase 5 (Claude market insights, needs API key), Phase 6 (demo hardening). Possible new direction: conversational/agentic command interface over the existing use-cases (under discussion).

## Architecture (one paragraph)

Kotlin + Compose + Material3, Clean Arch (`data`/`domain`/`ui`), MVVM + StateFlow, single-Activity Nav, Hilt, Room (v2). **The LLM is not in-app** — `LlmEngine` → `LlmHttpClient` (Ktor) POSTs to `llama-server` (Qwen 2.5 3B) on `http://127.0.0.1:8080/v1/chat/completions`. Reads are Room `Flow`s → `stateIn` → `collectAsStateWithLifecycle`. WorkManager runs via Hilt (`@HiltWorker` + `Configuration.Provider`; default startup initializer removed in the manifest). The cloud Claude API (Phase 5) reuses `NetworkModule`. `network_security_config.xml` permits cleartext to loopback only.

## Gotchas (read before changing things)

- **Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17.** AGP 9 breaks Hilt+KSP+Compose. Don't "upgrade." Vico pinned **2.1.3** (Kotlin-2.0 line; 3.x needs Kotlin 2.3).
- **llama-server must run ON the iQOO** (`CLAUDE.md` §1) — never a tethered Mac. The start command is `adb shell` from the Mac, but the process is phone-side and survives unplug. Phase 6 gate: airplane-mode + unplug → a sale still parses. Stop: `adb shell "pkill -f llama-server"`. Logs: `/sdcard/Download/llama-server.log`.
- **Validate prompt changes** with `scripts/validate-sale-prompt.py` (`adb forward tcp:8080 tcp:8080` first; sends Devanagari over HTTP). Keep its `SYSTEM_PROMPT` synced with `LlmEngine.SALE_SYSTEM_PROMPT`.
- **Room is v2 + `fallbackToDestructiveMigration`** — schema changes wipe dev data.
- **Driving Compose via adb is flaky.** `ModalBottomSheet` auto-scrolls on keyboard open, so coordinate `input tap`+`input text` land in the wrong field; uiautomator dumps come back empty. Prefer hand-testing UI; let the human drive data entry.
- **Forcing the periodic WorkManager job via `adb cmd jobscheduler run` is unreliable on OriginOS** (job id rotates, WM logs stripped). For a deterministic low-stock-alert trigger, add a debug-only expedited `OneTimeWorkRequest` of `InventoryAlertWorker` (Phase 6 hardening).

## Where things live

| Path | What |
|---|---|
| `…/data/remote/` | `LlmHttpClient`, `ClaudeApiClient` (P5), DTOs, `NetworkModule` |
| `…/data/llm/` | `LlmEngine` (system prompt), `SaleParser` |
| `…/data/db/` | Room entities, DAOs, `ArthaDatabase` (v2) |
| `…/data/worker/`, `…/data/notification/` | `InventoryAlertWorker`, `NotificationHelper` |
| `…/domain/` | repository interfaces, use-cases, models (`PnlSummary`, etc.) |
| `…/ui/` | `ArthaApp` (nav), `home/ entry/ inventory/ khata/ pnl/`, theme |
| `scripts/` | `start-llama-server.sh`, `validate-sale-prompt.py` |
| `docs/` | `STATUS.md`, `demo-runbook.md`, `superpowers/specs`, `superpowers/plans` |

## Device

iQOO 15, serial `10BFBG0CEL001DB`, Android 16, arm64-v8a. llama.cpp b9620 at `/data/local/tmp/llama/llama-b9620/`; model at `/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf` (2.4GB). On-device-LLM setup notes: `~/Desktop/CrazyStuff/iqoo-mobile/HANDOFF.md`.

# Handoff — Artha Kirana

Offline-first AI assistant for kirana shop owners. Speak/type a sale in Hindi → on-device LLM parses it → Room ledger/inventory/khata → vernacular summary. Built for the iQOO 15 (Snapdragon 8 Elite). Full spec: `CLAUDE.md`. Live state: `docs/STATUS.md`.

## Get running (~2 min)

```bash
cd /Users/archismanmidya/AndroidStudioProjects/Artha
adb devices                          # iQOO 15 = 10BFBG0CEL001DB
# Cloud is PRIMARY now — keys.properties (gitignored) already holds the OpenRouter key.
# The local llama-server is the OFFLINE FALLBACK only — optional unless testing fallback:
./scripts/start-llama-server.sh      # Qwen 2.5 3B on the phone (127.0.0.1:8080)
./gradlew :app:installDebug          # JDK 17, AGP 8.13 (pinned)
adb shell am start -n com.artha.kirana/.MainActivity
```

## State

> ✅ **CLOUD PIVOT + SCAN + VERGE UI + AGENTIC ASSISTANT + DATE-NAV — ALL SHIPPED & MERGED TO `main` (2026-06-14).** `feat/analytics-chat` was fast-forward-merged into `main` (42 commits). Everything below is on `main`, build-green (full unit suite passes, `assembleDebug` clean), and device-verified on the iQOO.

**LLM is now CLOUD-FIRST (on-device fallback kept):**
- ✅ **Cloud LLM primary.** All text parsing (sale / payment / intent / customer-name) + the agentic Assistant route through `data/remote/ChatClient` → `FallbackChatClient(CloudChatClient, LlmHttpClient)`: **OpenRouter → Claude Haiku 4.5** first, **local llama-server (Qwen 2.5 3B) as automatic fallback** (cloud error/timeout/blank-key/`FORCE_LOCAL_LLM` → local; preserves the offline story). Keys in **gitignored `keys.properties`** → BuildConfig (`OPENROUTER_KEY`/`OPENROUTER_MODEL=anthropic/claude-haiku-4.5`/`OPENROUTER_VISION_MODEL=anthropic/claude-opus-4.8`). Spec `docs/superpowers/specs/2026-06-14-cloud-llm-ocr-design.md`, plan `…/plans/2026-06-14-cloud-llm-ocr.md`.
- ✅ **Cloud-vision OCR scan (Claude Opus 4.8).** Home **SCAN** = sales: *customer-bill* (→ one customer + credit default) or *day-scribble* (→ mixed ledger) → `SaleEntry` → `LogSaleUseCase`. Inventory **SCAN CHALLAN** = supplier bill → editable cost + **sell price** per item → `LogPurchaseUseCase` (resolve-or-create item, restock, set prices). System-camera + FileProvider with iQOO cold-restart hardening (`util/ImageUtils`). `data/remote/CloudVisionClient.extractLedger/extractBill`.
- ✅ **Agentic Assistant.** The chat is a **cloud tool-calling agent** (`domain/usecase/AssistantAgentUseCase`, Haiku): 8 read-only `data/llm/ShopDataTools` (pnl / top-sellers / customer / day-trend / margins / low-stock / list-customers / inventory) + `propose_sale`/`propose_payment` (→ confirm cards, no silent writes). Reads local Room data, answers Hindi/Hinglish **with inline charts/cards** (`domain/model/AgentVisual` → bar chart / stats card rendered under the reply by `ui/assistant/AgentVisualCard`). Offline → falls back to the old 6-intent classifier (`RouteAssistantUseCase.classifyFallback`). **CRITICAL FIX:** agent tool requests serialize with `explicitNulls=false` — Anthropic-via-Bedrock ignores a `tool` message carrying `tool_calls:null` → model re-calls forever. Memory: `artha-agentic-assistant`.
- ✅ **Verge UI revamp.** Dark canvas `#131313` + mint/ultraviolet/yellow/hot-pink, **Anton/Hanken/Space-Mono** fonts (`res/font/`), flat 1px-hairline cards (no shadows), money semantics. All screens restyled. `ui/theme/{Color,Type,Theme,Components}.kt`. Memory: `artha-ui-design-system`.
- ✅ **Home date-navigator.** `◄ [ date ▾ ] ►` bar under the wordmark — step day (► disabled on today) or pick via Material3 calendar (≤ today); Home revenue + entries scope to the selected day. `HomeViewModel.selectedDay` + `TimeRange` day helpers + `SalesDao.observeBetween`.

**Earlier on `main` (foundation, unchanged):**
- Phases 0–2: sale loop (§18 5/5), Inventory, Khata, P&L (Vico), low-stock `InventoryAlertWorker`. Data-layer **v3** (customers FK, price snapshots, analytics use-cases). Editable Recent-entries (`EditSaleUseCase`). Auto-price.
- 🟢 **Phase 4 voice — working on-device.** whisper.cpp (vendored `app/src/main/cpp/whisper/`, arm64, **`-O3` forced in CMake**) + fine-tuned **`whisper-hindi-small` q5_1** at `/sdcard/Android/data/com.artha.kirana/files/ggml-hindi-small-q5_1.bin`. Mic in Sale Entry + Assistant. TODO: Hindi TTS, vernacular toggle.

**Owner gate / still TODO (not blockers):**
- Exercise the real camera→cloud scan end-to-end (a customer bill → sales/khata; a challan → priced restock). Re-verify §18 + Assistant fully cloud, then airplane-mode / `FORCE_LOCAL_LLM=true` → confirm local fallback.
- Optional: harden `SaleParser` to normalize off-enum `type` (udhaar→credit) — cloud `json_object` has no grammar enforcement (curl once returned `"type":"udhaar"`).
- Optional polish: dynamic ☁/📱 engine badge on the Assistant (still a static "AI · ON DEVICE" tag); strip debug `Artha-agent` Timber logging; Phase-5 Claude market-insights screen still unbuilt.

## Architecture (one paragraph)

Kotlin + Compose + Material3, Clean Arch (`data`/`domain`/`ui`), MVVM + StateFlow, single-Activity Nav, Hilt, Room (**v3**, `fallbackToDestructiveMigration`). **LLM is cloud-first via a `ChatClient` seam:** `IntentRouter`/`LlmEngine`/`AssistantAgentUseCase` inject `ChatClient`, bound to `FallbackChatClient(CloudChatClient → OpenRouter/Haiku, LlmHttpClient → local llama-server on 127.0.0.1:8080)`. Cloud vision (`CloudVisionClient`, Opus 4.8) powers the scan. All HTTP is one shared Ktor `HttpClient` (`di/NetworkModule`). Reads are Room `Flow`s → `stateIn` → `collectAsStateWithLifecycle`. WorkManager runs via Hilt (`@HiltWorker` + `Configuration.Provider`). `network_security_config.xml` permits cleartext to loopback only (OpenRouter is HTTPS).

## Gotchas (read before changing things)

- **Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17.** AGP 9 breaks Hilt+KSP+Compose. Don't "upgrade." Vico pinned **2.1.3** (Kotlin-2.0 line; 3.x needs Kotlin 2.3).
- **llama-server must run ON the iQOO** (`CLAUDE.md` §1) — never a tethered Mac. The start command is `adb shell` from the Mac, but the process is phone-side and survives unplug. Phase 6 gate: airplane-mode + unplug → a sale still parses. Stop: `adb shell "pkill -f llama-server"`. Logs: `/sdcard/Download/llama-server.log`.
- **The server is NOT persistent** — it was observed dying between sessions (process gone, `/health` → 000). Re-run `./scripts/start-llama-server.sh` (cold mmap ~10–20s). Note: that launcher script polls `/health` then can linger as a Mac-side shell after success — the phone server is detached and unaffected; `pkill -f start-llama-server.sh` cleans up the launcher without stopping the model. A tether-free persistent start (Termux/launcher) is still a Phase 6 item.
- **Keep `IntentRouter.INTENT_SYSTEM_PROMPT` in sync with `scripts/validate-intent-prompt.py`** (same discipline as the sale prompt). Intent router currently 10/10 live.
- **Validate prompt changes** with `scripts/validate-sale-prompt.py` (`adb forward tcp:8080 tcp:8080` first; sends Devanagari over HTTP). Keep its `SYSTEM_PROMPT` synced with `LlmEngine.SALE_SYSTEM_PROMPT`.
- **Room is v3 + `fallbackToDestructiveMigration`** — schema changes wipe dev data. Don't `adb uninstall` to reset (wipes the 181MB whisper model); use `adb shell run-as com.artha.kirana rm databases/artha.db*` then relaunch (debug `DemoDataSeeder` re-seeds an empty DB).
- **Cloud keys: gitignored `keys.properties` at repo root** holds `OPENROUTER_KEY` (never echo/commit), `OPENROUTER_MODEL` (Haiku text), `OPENROUTER_VISION_MODEL` (Opus 4.8 vision). `build.gradle.kts` reads them into BuildConfig. Rotate the key post-hackathon (it was shared in chat).
- **Agent tool requests MUST serialize with `explicitNulls=false`** (`CloudChatClient.agentJson`) — Anthropic-via-Bedrock silently ignores a `tool` message with `tool_calls:null`, causing the agent to re-call forever. Don't route them through the shared NetworkModule Json.
- **adb screencap occasionally returns 0 bytes on this device** — retry, or `screencap -p /sdcard/s.png && pull`. The Assistant Send button is hidden from `uiautomator dump` while the keyboard is open (tap it at the keyboard-open Y ≈ 1811 from a prior dump).
- **Driving Compose via adb is flaky.** `ModalBottomSheet` auto-scrolls on keyboard open, so coordinate `input tap`+`input text` land in the wrong field; uiautomator dumps come back empty. Prefer hand-testing UI; let the human drive data entry.
- **Forcing the periodic WorkManager job via `adb cmd jobscheduler run` is unreliable on OriginOS** (job id rotates, WM logs stripped). For a deterministic low-stock-alert trigger, add a debug-only expedited `OneTimeWorkRequest` of `InventoryAlertWorker` (Phase 6 hardening).

## Where things live

| Path | What |
|---|---|
| `…/data/remote/` | `ChatClient` (seam) + `CloudChatClient` (OpenRouter/Haiku, `completeWithTools`) + `FallbackChatClient` + `LlmHttpClient` (local); `CloudVisionClient` (Opus OCR); `dto/{ChatModels,OpenRouterModels,AgentModels}` |
| `…/data/llm/` | `LlmEngine`, `SaleParser`, `PaymentParser`, `IntentRouter`, **`ShopDataTools`** (8 read-only agent tools) |
| `…/data/db/` | Room entities, DAOs, `ArthaDatabase` (**v3**: customers, snapshots, FKs); `SalesDao.observeBetween` |
| `…/data/worker/`, `…/data/notification/` | `InventoryAlertWorker`, `NotificationHelper` |
| `…/domain/usecase/` | `ParseSaleEntryUseCase`, `LogSaleUseCase`, `EditSaleUseCase`, `RouteAssistantUseCase`, `GetPnlSummaryUseCase`, `PnlPeriodDetector`, `parseLeadingQty` |
| `…/domain/` | repository interfaces, models (`SaleEntry`, `AssistantIntent`, `AssistantResult`, `PnlSummary`) |
| `…/ui/` | `ArthaApp` (nav + center FAB), `assistant/` (chat), `common/EditableEntryCard`, `home/ entry/ inventory/ khata/ pnl/`, theme |
| `scripts/` | `start-llama-server.sh`, `validate-sale-prompt.py`, `validate-intent-prompt.py` |
| `docs/` | `STATUS.md`, `demo-runbook.md`, `superpowers/specs`, `superpowers/plans` |

## Device

iQOO 15, serial `10BFBG0CEL001DB`, Android 16, arm64-v8a. llama.cpp b9620 at `/data/local/tmp/llama/llama-b9620/`; model at `/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf` (2.4GB). On-device-LLM setup notes: `~/Desktop/CrazyStuff/iqoo-mobile/HANDOFF.md`.

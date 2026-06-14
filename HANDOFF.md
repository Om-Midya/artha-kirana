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

## State

> ⚠️ **ACTIVE PIVOT (2026-06-14): cloud LLM primary + local fallback + cloud OCR.** The on-device llama-server (Qwen 3B) is too flaky for the demo (cold-prefill timeouts → false "server offline", sampling wobble, dies between sessions). Going **cloud-primary (OpenRouter → Claude Haiku 4.5), local llama-server as fallback, cloud-vision OCR for bills.** Reference = a colleague's working hybrid app at `~/Desktop/CrazyStuff/artha-kirana`. **Brief + design + open questions: `docs/superpowers/specs/2026-06-14-cloud-llm-ocr-direction.md`. New-agent prompt: `docs/NEW-AGENT-PROMPT-cloud-llm.md`. NOT started — next agent brainstorms → spec → plan → build.**
>
> **Current branch:** `feat/analytics-chat` (15 commits, UNMERGED) atop merged `main`. It has data-layer v3 (customers/snapshots/analytics use-cases) + the Assistant + edit + auto-price (all merged to main earlier this session) PLUS analytics-in-chat (3 intents → text bubbles) + a debug `DemoDataSeeder`. On-device verified at the data+intent layers (intent classifier 16/16 live; seeder + analytics queries correct). The cloud pivot can build on this branch (the cloud client is a swap UNDER the existing `chat()` chokepoint — orthogonal to these features).

**On `main` (merged):**
- ✅ **Phase 0/1/2 done & merged.** Sale loop (type Hindi → on-device parse → confirm → Room → reactive Home, §18 = 5/5). Inventory (add/edit/restock + low-stock highlight), Khata (party list/detail + record-payment), P&L (today/week/month tabs + Vico chart), low-stock `InventoryAlertWorker`. Final review clean; unit tests green.
- 🤝 **Phase 3 (OCR/bill scanning) — a collaborator owns this.** Don't touch ML Kit / CameraX / `BillParser` / `BillScanScreen`.
- 🟢 **Phase 4 (voice) — working on-device.** whisper.cpp (vendored `app/src/main/cpp/whisper/`, arm64, **`-O3` forced in CMake** — debug installs default to `-O0` = ~30x slower) + **fine-tuned `whisper-hindi-small` ggml q5_1** (accurate Hindi). Model in app external-files dir (`adb push … /sdcard/Android/data/com.artha.kirana/files/ggml-hindi-small-q5_1.bin`; **NOT** /sdcard/Download — scoped storage). Mic in Sale Entry: record → whisper `hi` → `HindiNumbers.normalize` → Qwen w/ `json_schema` grammar → confirm. TODO: Hindi TTS, animation, vernacular toggle.
- ⬜ Phase 5 (Claude market insights, needs API key), Phase 6 (demo hardening).

**On `feat/assistant-layer` (built this session, reviewed, unit tests green, installed on iQOO — awaiting human UI walkthrough before merge):**
- 🟢 **Assistant layer (conversational tab).** Center protruding gold FAB tab → chat thread → **stateless two-stage intent router**: `IntentRouter.classify()` (enum `json_schema`, **10/10 live on-device**) → dispatch to existing use-cases. 3 intents: `log_sale` (reuses `ParseSaleEntryUseCase`), `record_payment` (`LlmEngine.parsePayment`), `query_pnl` (`PnlPeriodDetector` + `GetPnlSummaryUseCase`). Inline confirm cards reuse `EditableEntryCard`; mutations write via `LogSaleUseCase`/`applyRepayment` only on Confirm. Voice input reuses the whisper mic; replies text-only. LLM is **preloaded on screen open** (`warmUpLlm` primes the intent prefix cache). Spec/plan: `docs/superpowers/{specs,plans}/2026-06-14-artha-assistant*`.
- 🟢 **Editable "Recent entries" (Home).** Tap a recent sale → bottom sheet (reuses `EditableEntryCard`) → `EditSaleUseCase` reverses the old sale's inventory+khata effects and applies the edited ones (clean khata rewrite via `KhataRepository.reverseSaleEffect`), edit-in-place (same id/timestamp). Spec/plan: `…2026-06-14-artha-edit-recent-entries*`.
- ✅ **DONE since (all this session): data-layer v3** (auto-price, customers FK, price snapshots, 4 analytics use-cases — merged to `main`), **analytics-in-chat + seeder** (on `feat/analytics-chat`). 
- 🔜 **NEXT (handed off, not started): cloud LLM primary + local fallback + cloud OCR.** Brief: **`docs/superpowers/specs/2026-06-14-cloud-llm-ocr-direction.md`**. New-agent prompt: **`docs/NEW-AGENT-PROMPT-cloud-llm.md`**. Reference impl: `~/Desktop/CrazyStuff/artha-kirana` (OpenRouter + Claude Haiku, decorator fallback chain, cloud-vision OCR). Start by reading the brief, then brainstorm the open questions.

## Architecture (one paragraph)

Kotlin + Compose + Material3, Clean Arch (`data`/`domain`/`ui`), MVVM + StateFlow, single-Activity Nav, Hilt, Room (v2). **The LLM is not in-app** — `LlmEngine` → `LlmHttpClient` (Ktor) POSTs to `llama-server` (Qwen 2.5 3B) on `http://127.0.0.1:8080/v1/chat/completions`. Reads are Room `Flow`s → `stateIn` → `collectAsStateWithLifecycle`. WorkManager runs via Hilt (`@HiltWorker` + `Configuration.Provider`; default startup initializer removed in the manifest). The cloud Claude API (Phase 5) reuses `NetworkModule`. `network_security_config.xml` permits cleartext to loopback only.

## Gotchas (read before changing things)

- **Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17.** AGP 9 breaks Hilt+KSP+Compose. Don't "upgrade." Vico pinned **2.1.3** (Kotlin-2.0 line; 3.x needs Kotlin 2.3).
- **llama-server must run ON the iQOO** (`CLAUDE.md` §1) — never a tethered Mac. The start command is `adb shell` from the Mac, but the process is phone-side and survives unplug. Phase 6 gate: airplane-mode + unplug → a sale still parses. Stop: `adb shell "pkill -f llama-server"`. Logs: `/sdcard/Download/llama-server.log`.
- **The server is NOT persistent** — it was observed dying between sessions (process gone, `/health` → 000). Re-run `./scripts/start-llama-server.sh` (cold mmap ~10–20s). Note: that launcher script polls `/health` then can linger as a Mac-side shell after success — the phone server is detached and unaffected; `pkill -f start-llama-server.sh` cleans up the launcher without stopping the model. A tether-free persistent start (Termux/launcher) is still a Phase 6 item.
- **Keep `IntentRouter.INTENT_SYSTEM_PROMPT` in sync with `scripts/validate-intent-prompt.py`** (same discipline as the sale prompt). Intent router currently 10/10 live.
- **Validate prompt changes** with `scripts/validate-sale-prompt.py` (`adb forward tcp:8080 tcp:8080` first; sends Devanagari over HTTP). Keep its `SYSTEM_PROMPT` synced with `LlmEngine.SALE_SYSTEM_PROMPT`.
- **Room is v2 + `fallbackToDestructiveMigration`** — schema changes wipe dev data.
- **Driving Compose via adb is flaky.** `ModalBottomSheet` auto-scrolls on keyboard open, so coordinate `input tap`+`input text` land in the wrong field; uiautomator dumps come back empty. Prefer hand-testing UI; let the human drive data entry.
- **Forcing the periodic WorkManager job via `adb cmd jobscheduler run` is unreliable on OriginOS** (job id rotates, WM logs stripped). For a deterministic low-stock-alert trigger, add a debug-only expedited `OneTimeWorkRequest` of `InventoryAlertWorker` (Phase 6 hardening).

## Where things live

| Path | What |
|---|---|
| `…/data/remote/` | `LlmHttpClient`, `ClaudeApiClient` (P5), DTOs, `NetworkModule` |
| `…/data/llm/` | `LlmEngine` (sale + payment prompts/grammars), `SaleParser`, `PaymentParser`, `IntentRouter` |
| `…/data/db/` | Room entities, DAOs, `ArthaDatabase` (v2) |
| `…/data/worker/`, `…/data/notification/` | `InventoryAlertWorker`, `NotificationHelper` |
| `…/domain/usecase/` | `ParseSaleEntryUseCase`, `LogSaleUseCase`, `EditSaleUseCase`, `RouteAssistantUseCase`, `GetPnlSummaryUseCase`, `PnlPeriodDetector`, `parseLeadingQty` |
| `…/domain/` | repository interfaces, models (`SaleEntry`, `AssistantIntent`, `AssistantResult`, `PnlSummary`) |
| `…/ui/` | `ArthaApp` (nav + center FAB), `assistant/` (chat), `common/EditableEntryCard`, `home/ entry/ inventory/ khata/ pnl/`, theme |
| `scripts/` | `start-llama-server.sh`, `validate-sale-prompt.py`, `validate-intent-prompt.py` |
| `docs/` | `STATUS.md`, `demo-runbook.md`, `superpowers/specs`, `superpowers/plans` |

## Device

iQOO 15, serial `10BFBG0CEL001DB`, Android 16, arm64-v8a. llama.cpp b9620 at `/data/local/tmp/llama/llama-b9620/`; model at `/sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf` (2.4GB). On-device-LLM setup notes: `~/Desktop/CrazyStuff/iqoo-mobile/HANDOFF.md`.

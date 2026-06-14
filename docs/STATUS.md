# Artha Kirana — Status

**Updated:** 2026-06-14 · **Branch:** `main` (everything below fast-forward-merged; `feat/analytics-chat` was +42 commits) · **Device:** iQOO 15 (`10BFBG0CEL001DB`)

## ✅ SHIPPED & MERGED TO `main` (2026-06-14): cloud pivot + scan + Verge UI + agentic assistant + date-nav

Build-green (full unit suite passes, `assembleDebug` clean), device-verified on the iQOO. Highlights:
- **Cloud LLM primary + local fallback.** `ChatClient` → `FallbackChatClient(CloudChatClient = OpenRouter/Claude Haiku 4.5, LlmHttpClient = local llama-server)`. Cloud-first; on error/timeout/blank-key/`FORCE_LOCAL_LLM` → local. Keys in gitignored `keys.properties` → BuildConfig.
- **Cloud-vision OCR scan (Claude Opus 4.8).** Home SCAN = sales (customer-bill / day-scribble → ledger); Inventory SCAN CHALLAN = supplier bill → priced inventory restock. `CloudVisionClient`, `ImageUtils`, `LogPurchaseUseCase` (now sets sell price).
- **Agentic Assistant.** Cloud tool-calling agent (`AssistantAgentUseCase`) over 8 read-only `ShopDataTools` + propose-sale/payment drafts; answers in Hindi/Hinglish **with inline charts/cards** (`AgentVisual`). Offline → old 6-intent classifier. Critical fix: `explicitNulls=false` on agent requests.
- **Verge UI revamp** (canvas-black + mint/ultraviolet, Anton/Hanken/Space-Mono, flat hairlines) across all screens.
- **Home date-navigator** (◄ date ▾ ► + calendar; revenue/entries scope to the selected day).

Specs/plans: `docs/superpowers/specs|plans/2026-06-14-cloud-llm-ocr-*`. Memory: `artha-cloud-llm-pivot`, `artha-ui-design-system`, `artha-agentic-assistant`. **Owner gate (not blockers):** exercise real camera→cloud scan end-to-end; re-verify §18 + Assistant cloud, then airplane-mode/`FORCE_LOCAL_LLM` → local fallback.

## Progress

| Phase | Status |
|---|---|
| **Phase 0 — Foundation** | ✅ Done & device-verified |
| **Phase 1 — Core sale loop** | ✅ Done & device-verified (incl. Task 1.7 §18 = 5/5) |
| **Phase 2 — Inventory / Khata / P&L** | ✅ Done & merged to `main` (13/13 tasks, final review clean, unit tests green). Device-verified screens + record-payment + worker pipeline. Owner-verifying two §15 visuals (low-stock notification fire/clear; LLM-sale decrement/COGS). |
| **Phase 3 — Bill/ledger scanning** | ✅ **DONE (cloud-vision, merged).** Replaced the old ML-Kit plan with **Claude Opus 4.8** OCR. Home SCAN (customer-bill→credit / day-scribble→ledger → `LogSaleUseCase`); Inventory SCAN CHALLAN (supplier bill → editable cost+sell price → `LogPurchaseUseCase`, restocks inventory). System-camera + FileProvider + iQOO cold-restart hardening. Device-verified renders; owner gate = real-photo end-to-end. |
| **Phase 4 — Voice + vernacular** | 🟢 Voice pipeline WORKING on-device. whisper.cpp v1.7.4 (arm64, `-O3`) + fine-tuned **whisper-hindi-small q5_1**. Mic in Sale Entry + Assistant. **Still TODO:** Hindi TTS, recording animation, vernacular toggle. |
| Phase 5 — Market insights (Claude API) | ⬜ Not built (the agentic Assistant + ShopDataTools largely covers "insights" now). |
| Phase 6 — Demo hardening | 🟡 Partly — cloud removes the flaky-server risk; airplane-mode/`FORCE_LOCAL_LLM` fallback path exists; demo seeder present. |
| **Assistant — now AGENTIC** | ✅ **Merged.** Upgraded from the 6-intent classifier to a **cloud tool-calling agent** (`AssistantAgentUseCase`, Haiku): 8 read-only `ShopDataTools` + `propose_sale`/`propose_payment` (→ confirm cards). Answers in Hindi/Hinglish **with inline bar-chart / stats-card visuals** (`AgentVisual` + `AgentVisualCard`). Offline → falls back to the on-device intent classifier. Device-verified (e.g. "sabse zyada udhar kiska" → list_customers → "Ramesh ₹2,135"; "is mahine kya bika" → top-sellers bar chart). |
| **Verge UI revamp** | ✅ **Merged & device-verified.** Dark canvas + mint/ultraviolet/yellow/hot-pink, Anton/Hanken/Space-Mono fonts, flat hairline cards, money semantics. All screens. `ui/theme/{Color,Type,Theme,Components}`. |
| **Home date-navigator** | ✅ **Merged & device-verified.** ◄ [date ▾] ► bar — step day / pick via calendar (≤ today); Home revenue + entries scope to the selected day. |
| **Edit recent entries / data-layer v3 / auto-price / analytics** | ✅ Merged earlier this session (see below). |

## Data-layer restructure (in progress)

Brainstormed → spec → plan → executing (subagent-driven). **Spec:** `docs/superpowers/specs/2026-06-14-data-layer-restructure-design.md`. **Plan:** `docs/superpowers/plans/2026-06-14-data-layer-restructure.md` (16 tasks, 3 parts). Resolved decisions: "user profiles" = **customers** (new `customers` table + FK; not shop/settings); snapshot **unitPrice + unitCost** on sales; auto-price fills only when amount null + sellPrice>0; analytics = top-sellers / per-customer / margin / day-of-week; DB v2→v3 `fallbackToDestructiveMigration`.

- **Part 1 — Auto-price (Feature A): 🟢 DONE, merged to `main`.** When the LLM returns item+qty but no amount, `ParseSaleEntryUseCase` computes `amount = sellPrice × qty` from inventory (pure `computeAutoPrice`, injects `InventoryRepository`) before the confirm card — covers BOTH Sale Entry and the Assistant `log_sale` path. Commits `c431a1e`/`5a53d84`/`a4699bb`. **Task 3 (manual, owner, still pending):** on-device — "दो किलो चावल" (no price) shows ₹80 on the confirm card via Sale Entry AND Assistant; §18 still 5/5.
- **Part 2 — DB v3 (customers / FKs / price snapshots): 🟢 DONE, merged to `main`.** New `customers` table (id, name unique COLLATE NOCASE, nameHi?, phone?) as the identity hub; `CustomerRepository.resolveOrCreate(name)` idempotent name→id (used by LogSale/EditSale/Khata). `sales` gains `customerId` FK (SET_NULL) + `unitPrice`/`unitCost` snapshots (null when item unknown OR price ≤ 0) + indices (timestamp/itemId/customerId). `khata` gains `customerId` FK (CASCADE, unique — one ledger row per customer); `KhataRepositoryImpl.adjust` now resolves the customer and is keyed by `customerId` (public name-based API unchanged, so KhataScreen/Assistant untouched). Room v2→v3 `fallbackToDestructiveMigration` (wipes dev data). Dead `KhataDao.findByName` removed.
- **Part 3 — analytics: 🟢 DONE, merged to `main`.** DAO @Query + thin use-cases (building blocks; UI surfacing is a follow-up): `GetTopSellersUseCase` (revenue DESC), `GetItemMarginsUseCase` (uses snapshots, margin ASC to surface low-margin), `GetCustomerSummaryUseCase` (lifetime value + outstanding), `GetDayOfWeekTrendUseCase` (pure `bucketRevenueByWeekday`, Sun=index 0, repayments excluded). LLM stays parse-only — these are ordinary Room queries.
- **Review/verify:** 16 commits (`151b5e8`…`6d81b41`), each spec + code-quality reviewed (subagent-driven) + a final holistic integration review; full unit suite **73 green**; `assembleDebug` clean. Fast-forward merged to `main`; branch deleted. **STILL GATED on owner (manual on-device):** after the destructive v3 migration wipes dev data, re-enter a few sales and re-verify the full sale/credit/repayment/edit + Assistant flows on the iQOO (plus the pending Task 3 auto-price check: "दो किलो चावल" → ₹80 via Sale Entry & Assistant; §18 still 5/5).
- **Known follow-ups (non-blocking):** Devanagari sort under `COLLATE NOCASE` is codepoint-ordered (post-hackathon: locale collator); `CustomerSummary` has no `customerName` (add when a customer-list screen needs it); `CustomerRepository`/`KhataRepository` return entities directly (matches project convention).

Original direction brief (superseded): `docs/superpowers/specs/2026-06-14-data-layer-direction.md`. Design spec: `docs/superpowers/specs/2026-06-14-data-layer-restructure-design.md`. Plan: `docs/superpowers/plans/2026-06-14-data-layer-restructure.md`.

## Analytics-in-chat + demo seeder (on `feat/analytics-chat`, 15 commits, UNMERGED)

🟢 **Built & on-device-verified (data+intent layers).** 3 analytics intents wired into the Assistant (`query_top_sellers` / `query_customer` / `query_day_trend`; margins stays verify-only) rendered as Hindi text bubbles via `AnalyticsChatFormatter`; cloud-free Room queries via the 4 analytics use-cases. Debug-only `DemoDataSeeder` (~28 dated sales / 5 items / 4 customers / khata / purchases). **Live intent classifier 16/16 on the 3B**; seeder data + all 4 analytics queries verified correct on-device. Two bugs fixed this session: ranking queries now default THIS_MONTH not TODAY (`0c4ccff`); SALE prompt warmed on Assistant open to stop the cold-prefill false-"offline" (`a2cc6f4`). Spec/plan: `docs/superpowers/{specs,plans}/2026-06-14-analytics-chat-seeder*`. **Awaiting:** final visual chat-bubble eyeball + merge decision (now likely folded into the cloud pivot).

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

- **LLM = CLOUD-FIRST (2026-06-14 pivot).** OpenRouter → Claude Haiku 4.5 (text) / Opus 4.8 (vision) is PRIMARY; the on-device `llama-server` (Qwen 2.5 3B, `127.0.0.1:8080`) is the automatic FALLBACK. Deliberate reversal of CLAUDE.md §1's "nothing leaves the phone" pitch — the local fallback preserves an offline story. Memory/spec: `artha-cloud-llm-pivot`, `docs/superpowers/specs/2026-06-14-cloud-llm-ocr-design.md`.
- **AGP 9.0.1 → 8.13.0** (AGP 9 breaks Hilt/KSP/Compose).
- **SQLCipher deferred** behind a swap-in seam in `DatabaseModule`.
- **Vico (charts) deferred to Phase 2** — 3.1.0 needs Kotlin 2.3.x; pick a Kotlin-2.0-compatible version.
- **Room DB is version 3** (customers table + FKs + price snapshots on sales; `fallbackToDestructiveMigration`).

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

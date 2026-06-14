# New-Agent Prompt — Cloud LLM (primary) + Local Fallback + Cloud OCR

> Paste the block below to a fresh agent to start this work.

---

You're continuing **Artha Kirana** — an AI assistant for kirana (corner-shop) owners on the iQOO 15. The app is at `/Users/archismanmidya/AndroidStudioProjects/Artha` (Kotlin, Jetpack Compose, Material3, Clean Architecture data/domain/ui, **Hilt** DI, **Ktor**+kotlinx.serialization HTTP, **Room v3**). Spec: `CLAUDE.md`.

## Your job

The on-device LLM (llama-server, Qwen 2.5 3B on `127.0.0.1:8080`) is **too flaky for the hackathon demo** — cold-prefill of the big sale prompt times out into false "server offline", temp-0.1 sampling wobbles, and the server dies between sessions. **Pivot to:**
1. **Cloud LLM = PRIMARY** parser for all text (sale / payment / intent classification / customer-name). Use **OpenRouter → `anthropic/claude-haiku-4.5`** (the colleague's proven setup) unless you and the user decide on direct Anthropic.
2. **Local llama-server = FALLBACK** (keep it; it's demoted, not removed — preserves an offline story).
3. **Cloud-vision OCR** for bill scanning (cloud-only for now — no ML Kit path).
4. Optionally adopt the colleague's **UI polish** (Indian-rupee formatting, online/on-device badges, glass theme) — lowest priority.

This deliberately reverses CLAUDE.md §1's "nothing leaves the phone" pitch; the user has signed off (demo reliability > purity; local fallback keeps an offline narrative).

## READ FIRST, IN ORDER

1. **`docs/superpowers/specs/2026-06-14-cloud-llm-ocr-direction.md`** — your direction brief: the goal, the colleague's reference implementation (cloud client, fallback chain, cloud OCR), OUR project's exact integration seam, a proposed A→C decomposition, and **8 open questions you MUST resolve with the user before designing.**
2. **`HANDOFF.md`** + **`docs/STATUS.md`** — current state (the ⚠️ PIVOT callout; what's merged vs on the `feat/analytics-chat` branch).
3. **`CLAUDE.md`** — canonical spec (note §1 privacy pitch you're amending, §5 prompts, §16 standards).
4. **The colleague's reference app** (read-only): `~/Desktop/CrazyStuff/artha-kirana` — especially `domain/cloud/OpenRouterClient.kt`, `domain/parser/{CloudTransactionParser,TransactionParsing,RuleBasedTransactionParser}.kt`, `domain/vision/{OllamaVisionClient,ImageUtils}.kt`, `ui/scan/ScanScreen.kt`, `ui/components/Components.kt`, `ARCHITECTURE_CONTRACT_V2.md`. (Its `PLAN.md` is stale/unrelated — ignore.)
5. Persistent memory will surface — esp. `artha-analytics-chat-seeder`, `artha-data-layer-direction`.

## Key facts you'll need (verified)

- **The integration seam:** `IntentRouter` and `LlmEngine` (in `data/llm/`) both `@Inject` the concrete `data/remote/LlmHttpClient`, whose only method is `suspend fun chat(system, user, responseFormat: JsonElement?): String` (throws `LlmUnavailableException`). EVERYTHING funnels through `chat`. Introduce a `ChatClient` interface with that signature → make `LlmHttpClient` the LOCAL impl → add `CloudChatClient` (Ktor, OpenRouter) → add `FallbackChatClient(cloud, local)` (cloud-first, fall back on exception/timeout/blank-key) → bind in Hilt → inject the interface. One change flips the whole app to cloud-primary + local-fallback.
- **`ClaudeApiClient` was never built** (Phase 5 stub) — cloud client is greenfield. Build on the shared Ktor `HttpClient` in `di/NetworkModule.kt`.
- **`response_format` mismatch:** local uses llama.cpp `json_schema` grammar; cloud can't — send `{type:"json_object"}` (or nothing for vision) and rely on the system prompt + our robust `util/JsonParser.extractJson`. Our parsers (`SaleParser`/`PaymentParser`/`parseIntent`/`parseCustomerName`) already extract JSON from free text → tolerant.
- **Keep OUR schema:** the cloud must produce the same JSON our `SaleParser` expects (our `SaleEntry(item, qty, amount, type, party)` + `SALE_SYSTEM_PROMPT`), NOT the colleague's `ParsedTransaction`. A frontier model will parse our schema *more* reliably than the 3B did.
- **OCR is greenfield in THIS repo:** CameraX (1.4.1) + ML Kit (16.0.1) deps are present but NO Phase 3 OCR code exists here (the "collaborator-owned, don't touch" note refers to their separate fork — confirm with the user). Build cloud-vision bill-scan fresh: capture → base64 (port the colleague's `ImageUtils` + iQOO capture hardening) → `CloudVisionClient.extractBill` → editable confirm sheet → `LogPurchaseUseCase` → inventory.
- **Keys:** add `OPENROUTER_KEY`/`OPENROUTER_MODEL`/`OPENROUTER_VISION_MODEL` BuildConfig fields from a **gitignored `keys.properties`** (mirror the colleague's `build.gradle.kts`). **BLOCKER: ask the user for an OpenRouter API key.** INTERNET permission already granted; OpenRouter is HTTPS (network_security_config loopback-cleartext-only is unaffected).
- **Current branch** `feat/analytics-chat` (15 commits, unmerged) atop merged `main`. Build the cloud pivot on it (orthogonal). Confirm branch base with the user.

## HOW TO WORK (superpowers)

This is creative architectural work. **Brainstorm first** (resolve the brief's 8 open questions with the user — esp. provider OpenRouter-vs-Anthropic, API key, cloud timeout-before-fallback, OCR scope, UI adoption, branch base) → **writing-plans** → **subagent-driven-development** (fresh subagent per task + spec & code-quality review gates; TDD the fallback decision + cloud JSON parse; build-verify + on-device for the camera/scan UI). Use Context7 to verify any Ktor/OpenRouter/CameraX API before coding. Commit per task. This is exactly the pattern used for data-layer v3 and analytics-chat this session (see those specs/plans as templates).

## Decomposition (from the brief — each its own spec→plan→build)

- **A. Cloud chat client + fallback seam (DO FIRST — unblocks the demo):** `ChatClient` interface, `CloudChatClient` (OpenRouter/Haiku/json_object), `FallbackChatClient` (cloud→local), Hilt binding, inject into `IntentRouter`+`LlmEngine`. Verify all four text paths parse via cloud, and that blank-key/airplane falls back to local. Re-verify §18 + the Assistant flows (sale/payment/intent/customer/analytics) + edit-recent-entries — all route through the seam.
- **B. Cloud OCR bill scan (cloud-only):** `CloudVisionClient.extractBill`, `ImageUtils`, capture screen, editable `ScanConfirmSheet` → `LogPurchaseUseCase` → inventory. Map vision items to OUR `PurchaseEntity`/`ItemEntity`.
- **C. UI polish (optional):** `asRupees()` Indian grouping, online/on-device badge, optionally the glass theme.

## GOTCHAS

- Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3 / Room v3 `fallbackToDestructiveMigration`. Don't upgrade.
- Don't regress the §18 sale path, the Assistant flows, edit-recent-entries, or the analytics-chat features — all route through the `chat()` chokepoint you're abstracting. Re-verify after A.
- Keep the local llama-server path intact as the fallback (`scripts/start-llama-server.sh`; the `warmUpSale`/`warmUp` priming still helps the fallback). Device serial `10BFBG0CEL001DB`.
- To get fresh demo data, the debug `DemoDataSeeder` runs on first launch of an empty DB. **Don't `adb uninstall` to reset** — it wipes the 181MB whisper model at `/sdcard/Android/data/com.artha.kirana/files/ggml-hindi-small-q5_1.bin` (source on Mac: `~/Library/Android/sdk/ggml-hindi-small-q5_1.bin`). Instead `adb shell run-as com.artha.kirana rm databases/artha.db*` then relaunch.
- LLM (cloud or local) is parse/classify/vision only — analytics stay ordinary Room queries.
- Driving Compose via adb is flaky; `uiautomator dump` DOES read the Assistant chat screen (text nodes) — useful for verifying without a human, but have the human eyeball UI.

Start by reading the four docs + the colleague's reference files, then **brainstorm the 8 open questions with the user** (especially: confirm OpenRouter+Haiku, get the API key, pick the cloud-timeout-before-fallback, confirm OCR scope and branch base) before writing any code.

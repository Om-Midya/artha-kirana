# Cloud LLM (primary) + Local Fallback + Cloud OCR — Direction Brief

**Date:** 2026-06-14 · **Status:** 🔜 DIRECTION ONLY — next agent must brainstorm (resolve open questions) → spec → plan → build. Do NOT jump to code.
**Why:** The on-device llama-server (Qwen 2.5 3B) is too flaky for the hackathon demo (cold-prefill timeouts → false "server offline", sampling wobble, server dies between sessions). Decision: **cloud LLM primary, local LLM fallback**, plus **cloud-vision OCR** for bill scanning. This is a deliberate, demo-driven reversal of `CLAUDE.md §1`'s "nothing leaves the phone" pitch — see §6.

---

## 1. Goal (verbatim user ask, 2026-06-14)

> "this local llm is failing … i have another artha kirana by a colleague that runs on cloud llm api, also has ocr, go through that add the cloud llm stuff … add fallback to local model, add the ocr cloud only for now, and the ui there looks good too, update the current status, handoff then create a new prompt for new agent"

Decoded:
1. **Cloud LLM = primary** parser (text: sale/payment/intent/customer-name extraction).
2. **Local llama-server = fallback** when cloud fails (offline/timeout/error).
3. **Cloud OCR** for bills (vision LLM) — cloud-only for now (no ML Kit path yet).
4. Optionally adopt the **colleague's UI polish** (glassmorphism, Indian-rupee formatting, online/on-device badges).

## 2. Reference implementation (the colleague's project)

Repo: `/Users/archismanmidya/Desktop/CrazyStuff/artha-kirana` (`github.com/whybepb-rktm/artha-kirana`, commit `622045a` "voice + bill OCR + khata + P&L, hybrid on-device/cloud"). **Read-only reference — do not modify it.** It is a *separate* app (manual ServiceLocator DI, raw `HttpURLConnection`, its own Room schema), so we PORT the approach, not the code wholesale. Real spec = `ARCHITECTURE_CONTRACT_V2.md` there; ignore its `PLAN.md` (stale, unrelated React-Native pitch).

### Cloud client — `domain/cloud/OpenRouterClient.kt`
- **Gateway: OpenRouter** (`https://openrouter.ai/api/v1/chat/completions`, OpenAI-compatible). Model **`anthropic/claude-haiku-4.5`** for BOTH text and vision (cheap, fast, one key).
- Headers: `Authorization: Bearer $key`, `Content-Type: application/json`, `HTTP-Referer: https://artha.kirana`, `X-Title: Artha Kirana`. Timeouts: connect 5s, read 60s.
- Text body: `{model, response_format:{type:"json_object"}, messages:[{role:system,...},{role:user,...}]}`. Vision body OMITS `response_format` and sends the image as a `data:image/jpeg;base64,…` content part.
- Response: `choices[0].message.content` → `stripFences()` → `JSONObject`. All public methods catch everything and return `null`/`error=…` so callers fall back. **Returns "No OpenRouter key configured" gracefully when key is blank** → airplane-mode safe.
- Key/model from gitignored `keys.properties` → `BuildConfig.OPENROUTER_KEY / OPENROUTER_MODEL / OPENROUTER_VISION_MODEL` (defaults `anthropic/claude-haiku-4.5`).

### Fallback chain — decorator pattern in `di/ServiceLocator.kt`
```
CloudTransactionParser(cloud, fallback = OllamaTransactionParser(fallback = RuleBasedTransactionParser()))
```
Order: **Cloud (Claude Haiku) → local Ollama gemma3:4b → rule-based heuristic.** Each layer's trigger = the layer above returns `null`/throws. No flags; "off switch" = blank key. `CloudTransactionParser.parse = cloud.parseTransaction(input) ?: fallback.parse(input)`.
- Shared `domain/parser/TransactionParsing.kt` holds the **single SYSTEM_PROMPT** + `mapJson()` so cloud and local are drop-in interchangeable. (Prompt + `ParsedTransaction` shape quoted in the exploration report; reuse as a starting point but reconcile with OUR `SaleEntry` schema — see §4.)

### Cloud OCR — `OpenRouterClient.extractBill(imageBase64)` + `domain/vision/ImageUtils.kt` + `ui/scan/ScanScreen.kt`
- Capture: system camera via `ActivityResultContracts.TakePicture()` + `FileProvider` (NOT CameraX). Heavy **iQOO/vivo capture hardening** (camera returns ok=false on success, cold-restarts the activity → capture gated on a `pending_uri` in SharedPreferences, ingested via result callback + ON_RESUME + first-composition `LaunchedEffect`). Worth porting that resilience.
- Encode: `ImageUtils.uriToBase64` — `ImageDecoder`/`BitmapFactory`, EXIF rotate, downscale longest side ≤ maxDim, JPEG compress, `Base64.NO_WRAP`. Bills use `maxDim=1568, quality=90`.
- Bill vision PROMPT (`BILL_SYSTEM`, verbatim in the exploration report) → JSON `{"items":[{name,qty,unit,unitPrice,amount}],"total":number|null}`. Strong anti-hallucination rule ("Read ONLY what is written … omit illegible lines rather than guess").
- Result → `VisionResult(items, total, …)` → `ScanViewModel.confirm` → `repo.addOrRestock(name, qty, unit, unitPrice)` per item.
- The colleague's BILL→cloud, KHATA(handwritten)→on-device split is a privacy nicety; **we only need the cloud BILL path** (`extractBill` is self-sufficient).

### UI (optional polish) — `ui/theme/*`, `ui/components/Components.kt`
- Dark glassmorphism, yellow `#F5C518` on `#0B0C10`, cloud-accent `#58A6FF` for online features. `Modifier.glass()` primitive (note: NO `Modifier.shadow()` — renders as a black box on this iQOO GPU). `Double.asRupees()` → Indian digit grouping (`₹12,40,000`). Online-vs-on-device badge contrast (surfaces the parser's `engine` string).

## 3. Our project's integration points (where the port lands)

Target repo: `/Users/archismanmidya/AndroidStudioProjects/Artha` (THIS one). Stack: **Hilt DI, Ktor (OkHttp engine) + kotlinx.serialization, Room v3**. Differs from the colleague's (manual DI + raw HttpURLConnection) — prefer OUR stack (Ktor/Hilt) for the port.

- **Single LLM chokepoint:** `IntentRouter` and `LlmEngine` (in `data/llm/`) both `@Inject` the concrete `data/remote/LlmHttpClient`, whose one method is:
  ```kotlin
  suspend fun chat(system: String, user: String, responseFormat: JsonElement? = null): String  // throws LlmUnavailableException
  ```
  Everything (sale parse, payment, intent classify, customer-name) funnels through `chat`. **This is the seam:** introduce a `ChatClient` interface with that signature; make today's `LlmHttpClient` the LOCAL impl; add a `CloudChatClient`; add a `FallbackChatClient(cloud, local)` that tries cloud first and falls back. Bind `ChatClient`→`FallbackChatClient` in Hilt and inject the interface into `IntentRouter`/`LlmEngine`. One change flips the whole app to cloud-primary + local-fallback.
- **`ClaudeApiClient` does NOT exist yet** (Phase 5 was never built) — cloud client is greenfield. Build `CloudChatClient` with Ktor on the shared `HttpClient` from `di/NetworkModule.kt` (already has ContentNegotiation + HttpTimeout 60s/10s).
- **`response_format` mismatch (IMPORTANT):** local uses llama.cpp **`json_schema`** grammar (strict). OpenRouter/Claude can't take arbitrary json_schema the same way — use **`{type:"json_object"}`** (or nothing for vision) and rely on the system prompt + our robust `util/JsonParser.extractJson` (which already strips fences/preamble). So `CloudChatClient` should translate: "responseFormat present → send json_object", ignore the schema details. The parsers (`SaleParser`, `PaymentParser`, `IntentRouter.parseIntent`, `parseCustomerName`) already extract JSON from free text → they tolerate this.
- **Keys/config:** our `app/build.gradle.kts` uses `buildConfigField` (e.g. `LLM_BASE_URL`) + `buildConfig=true`. Add `OPENROUTER_KEY`/`OPENROUTER_MODEL`/`OPENROUTER_VISION_MODEL` from a **gitignored `keys.properties`** (mirror the colleague's `build.gradle.kts` block). INTERNET permission already granted; OpenRouter is HTTPS so `network_security_config` (loopback-cleartext-only) is unaffected.
- **OCR is greenfield in THIS repo:** CameraX (1.4.1) + ML Kit text-recognition (16.0.1) deps are present, but **no Phase 3 OCR code exists here** (`BillScanScreen`/`BillParser`/`BillOcrEngine`/`ScanViewModel` not found). So build the cloud-vision bill-scan fresh (CameraX or system-camera capture → base64 → `CloudVisionClient.extractBill` → editable `ScanConfirmSheet` → `LogPurchaseUseCase` → inventory). No collaborator collision in this repo (the "Phase 3 collaborator-owned, don't touch" note in CLAUDE.md refers to their separate fork — confirm with the user before assuming it's safe).
- **Our entities** (data-layer v3, just merged-ish): `ItemEntity`, `PurchaseEntity`, `SaleEntity` (customerId/unitPrice/unitCost), `CustomerEntity`. The cloud parser output must map to OUR `SaleEntry(item, qty, amount, type, party)` (used by `ParseSaleEntryUseCase`/`LogSaleUseCase`) — NOT the colleague's `ParsedTransaction`. Keep our existing `SALE_SYSTEM_PROMPT`/schema; the cloud just needs to produce the same JSON our `SaleParser` expects. (A bigger frontier model will likely parse our schema MORE reliably than the 3B did.)

## 4. Proposed decomposition (each its own spec → plan → build)

**A. Cloud chat client + fallback seam (DO FIRST — unblocks the demo).**
`ChatClient` interface; `LlmHttpClient` → local impl; `CloudChatClient` (Ktor, OpenRouter, Haiku, json_object); `FallbackChatClient(cloud, local)` (cloud-first, fall back on exception/timeout/blank); Hilt binding; inject interface into `IntentRouter` + `LlmEngine`. Keep OUR prompts/schemas. TDD the fallback decision + the cloud client's JSON parse. Verify all four paths (sale/payment/intent/customer) still parse via cloud, and that killing the cloud (blank key / airplane) falls back to local.

**B. Cloud OCR bill scan (cloud-only).**
`CloudVisionClient.extractBill(base64): List<ParsedPurchaseItem>` (Ktor multimodal, data-URI, BILL_SYSTEM prompt) + `ImageUtils` (base64/EXIF/downscale, port the iQOO hardening) + a `BillScanScreen` (capture) + `ScanConfirmSheet` (editable items) → `LogPurchaseUseCase` → inventory. Map vision items to OUR `PurchaseEntity`/`ItemEntity` (reuse/extend an `addOrRestock`-style inventory op).

**C. UI polish (optional, lower priority).**
Selectively adopt `asRupees()` (Indian grouping), the online/on-device badge (surface which backend answered), and/or the glass theme. Don't block the demo on this.

**Recommended order:** A → B → (C if time).

## 5. Open questions

**RESOLVED (user, 2026-06-14):**
1. ✅ **Provider = OpenRouter + `anthropic/claude-haiku-4.5`** (one key for text + vision).
2. ✅ **API key supplied** — stored in gitignored `keys.properties` (`OPENROUTER_KEY` + `OPENROUTER_MODEL` + `OPENROUTER_VISION_MODEL`, both models = Haiku 4.5). Task A wires `build.gradle.kts` to read it into BuildConfig. Never commit/echo the value.
8. ✅ **Branch = stay on `feat/analytics-chat`** (don't merge to main first).

**STILL OPEN (brainstorm before designing):**
3. **Fallback trigger + cloud timeout:** fall back to local on cloud exception/timeout/blank-key. What cloud timeout before falling back? Colleague uses 60s read; for snappy fallback consider ~8–12s so a slow/no-network cloud drops to local fast. Also a "force local" debug toggle?
4. **`response_format`:** confirm json_object + our `JsonParser` is enough (no json_schema on cloud). (Recommend yes — parsers already tolerant.)
5. **OCR scope:** cloud bill scan only (no ML Kit, no handwritten-khata-on-device for now)? Capture via CameraX (our deps) or port the colleague's system-camera+FileProvider hardening? Coordinate with the Phase 3 collaborator?
6. **Schema:** keep OUR `SaleEntry`/`SALE_SYSTEM_PROMPT` (just routed to the cloud), or adopt the colleague's richer `ParsedTransaction` (adds EXPENSE type, paymentType UPI/CASH)? Recommend keep ours for now (minimal churn; our DB + use-cases already speak `SaleEntry`).
7. **UI adoption:** how much of the colleague's theme to pull in (just `asRupees()`+badges, or the full glass theme)?

## 6. Constraints / gotchas

- **Privacy-pitch reversal:** cloud-primary sends transaction text + bill images off-device. The colleague mitigates by keeping handwritten *khata* on-device and only sending bills/trends to cloud. Decide how much to say in the pitch; the **local fallback preserves an offline story** ("works offline, just slower/less accurate"). Update CLAUDE.md §1's framing.
- **Don't regress** the §18 sale path, the Assistant flows (intent/sale/payment/customer/analytics), or edit-recent-entries — all route through the `chat` chokepoint you're abstracting. Re-verify after A.
- **Keep the local llama-server path intact** as the fallback (it's not going away; it's demoted). The `warmUpSale`/`warmUp` work still applies to the local fallback.
- Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3 / Room v3 fallbackToDestructiveMigration. Don't upgrade.
- LLM (cloud or local) is parse/classify/vision only — analytics stay ordinary Room queries.
- Process: superpowers brainstorming → writing-plans → subagent-driven-development (spec + code-quality review gates per task), exactly as data-layer v3 and analytics-chat were built this session.

## 7. Pointers

- Colleague's cloud client: `…/CrazyStuff/artha-kirana/app/src/main/java/com/artha/kirana/domain/cloud/OpenRouterClient.kt`; parsers `…/domain/parser/{TransactionParser,TransactionParsing,CloudTransactionParser,OllamaTransactionParser,RuleBasedTransactionParser,ParsedTransaction}.kt`; vision `…/domain/vision/{OllamaVisionClient,VisionModels,ImageUtils}.kt` + `…/ui/scan/{ScanScreen,ScanViewModel}.kt`; UI `…/ui/theme/*` + `…/ui/components/Components.kt`; contract `…/ARCHITECTURE_CONTRACT_V2.md`.
- Our seam: `app/src/main/java/com/artha/kirana/data/remote/LlmHttpClient.kt` (+ `NetworkModule.kt`), `data/llm/{LlmEngine,IntentRouter,SaleParser,PaymentParser}.kt`, `util/JsonParser.kt`, `domain/usecase/{ParseSaleEntryUseCase,LogSaleUseCase,LogPurchaseUseCase?}.kt`, `app/build.gradle.kts` (BuildConfig/keys).
- This session's pattern templates: `docs/superpowers/specs|plans/2026-06-14-data-layer-restructure*` and `…-analytics-chat-seeder*`.

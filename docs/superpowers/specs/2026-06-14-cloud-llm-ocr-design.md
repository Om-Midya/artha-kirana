# Cloud LLM (primary) + Local Fallback + Cloud OCR — Design Spec

**Date:** 2026-06-14 · **Status:** ✅ APPROVED — ready for plan → build
**Branch:** `feat/analytics-chat` (stay; build the pivot here)
**Supersedes the open questions in:** `docs/superpowers/specs/2026-06-14-cloud-llm-ocr-direction.md` (direction brief). This is the concrete design.

---

## 1. Goal

The on-device llama-server (Qwen 2.5 3B) is too flaky for the hackathon demo (cold-prefill timeouts → false "server offline", sampling wobble, dies between sessions). Pivot to:

1. **Cloud LLM = PRIMARY** parser for all text (sale / payment / intent classification / customer-name) via **OpenRouter → `anthropic/claude-haiku-4.5`**.
2. **Local llama-server = FALLBACK** (demoted, not removed — preserves an offline story).
3. **Cloud-vision OCR** for bill scanning (cloud-only; greenfield in this repo).
4. **Light UI polish**: Indian-rupee formatting + an online/on-device engine badge.

This deliberately amends `CLAUDE.md §1`'s "nothing leaves the phone" pitch; the user has signed off (demo reliability > purity; the local fallback keeps an offline narrative).

## 2. Resolved decisions (user, 2026-06-14)

| # | Decision | Value |
|---|---|---|
| Provider | gateway + model | **OpenRouter** + `anthropic/claude-haiku-4.5` (text + vision, one key) |
| API key | storage | gitignored `keys.properties` at repo root (already present; never echo/commit the value) |
| Branch | base | stay on `feat/analytics-chat` |
| Fallback timeout | cloud wait before dropping to local | **~10s** per-request (blank-key / hard error falls back instantly) |
| OCR capture | mechanism | **Port the colleague's system-camera + FileProvider** with iQOO/vivo capture hardening (NOT CameraX) |
| UI polish | scope | **`asRupees()` + engine badge only** (no glass theme) |
| Stopping point | verification gate | **Build A + B, then owner verifies on-device** |
| `response_format` | cloud | send `{"type":"json_object"}` (drop llama.cpp json_schema); rely on prompt + `JsonParser.extractJson` |
| Schema | sale shape | **keep OUR `SaleEntry` + `SALE_SYSTEM_PROMPT`** (a frontier model parses it more reliably than the 3B did) |

## 3. Current state (verified in code, 2026-06-14)

- **The seam:** `IntentRouter` and `LlmEngine` (both in `data/llm/`) `@Inject` the concrete `data/remote/LlmHttpClient`. Everything funnels through:
  ```kotlin
  suspend fun chat(system: String, user: String, responseFormat: JsonElement? = null): String  // throws LlmUnavailableException
  ```
  `LlmEngine` ALSO calls `client.health(): Boolean`. `LlmHttpClient.chat` already retries 3× (300 ms) and throws `LlmUnavailableException` on exhaustion.
- **Local response_format:** llama.cpp `json_schema` grammars, built via `buildJsonObject` in the `LlmEngine`/`IntentRouter` companions (SALE / INTENT / PAYMENT / CUSTOMER_NAME). Cloud can't take these → translate to `json_object`.
- **Shared Ktor client:** `di/NetworkModule.kt` provides one `HttpClient(OkHttp)` (ContentNegotiation json, `requestTimeoutMillis = 60_000`, `connectTimeoutMillis = 10_000`). DTOs in `data/remote/dto/ChatModels.kt`.
- **`ClaudeApiClient` was never built** (Phase 5 stub) → cloud client is greenfield.
- **OCR is greenfield here.** CameraX (1.4.1) + ML Kit (16.0.1) deps are present but no Phase 3 code exists. `LogPurchaseUseCase` does **not** exist; there is no `PurchaseRepository` (only `PurchasesDao.insert`). `InventoryRepository` already exposes `findByName`, `addItem`, `incrementStock`, `updateItem` — enough to compose a restock without new repo methods.
- **`keys.properties`** exists at repo root and is gitignored (`git check-ignore` confirms). Holds `OPENROUTER_KEY` / `OPENROUTER_MODEL` / `OPENROUTER_VISION_MODEL`. `build.gradle.kts` does **not** yet read them into BuildConfig.

## 4. Architecture — Task A: Cloud chat seam

```
IntentRouter ─┐
              ├─inject→ ChatClient (interface)  ◄── @Binds ── FallbackChatClient
LlmEngine ────┘                                                   │ cloud-first
                                                          ┌────────┴────────┐
                                                    CloudChatClient    LlmHttpClient
                                                    (OpenRouter/Haiku)  (local, today's class)
```

### 4.1 `ChatClient` interface — `data/remote/ChatClient.kt`
```kotlin
enum class LlmEngineKind { CLOUD, ON_DEVICE, NONE }

interface ChatClient {
    suspend fun chat(system: String, user: String, responseFormat: JsonElement? = null): String  // throws LlmUnavailableException
    suspend fun health(): Boolean
    val engine: StateFlow<LlmEngineKind>   // which backend answered the most recent chat()
}
```
`LlmUnavailableException` stays in `data/remote` (already there).

### 4.2 `LlmHttpClient` → local impl
- Implement `ChatClient`. Keep its existing `chat`/`health`/retry behaviour verbatim.
- It does not own the shared `engine` StateFlow (the fallback does). `LlmHttpClient.engine` returns a constant `MutableStateFlow(ON_DEVICE).asStateFlow()` for interface conformance, but it is **never injected directly** post-swap — only `FallbackChatClient`'s `engine` is read by the UI. (Acceptable; keeps the interface uniform.)

### 4.3 `CloudChatClient` — `data/remote/CloudChatClient.kt`
- `@Inject` the shared `HttpClient`.
- POST `https://openrouter.ai/api/v1/chat/completions` with headers `Authorization: Bearer ${BuildConfig.OPENROUTER_KEY}`, `Content-Type: application/json`, `HTTP-Referer: https://artha.kirana`, `X-Title: Artha Kirana`.
- Body: `{model: BuildConfig.OPENROUTER_MODEL, messages:[{system},{user}], response_format: <translated>}`.
  - **Translation:** `responseFormat != null → {"type":"json_object"}`; `responseFormat == null → omit`. Never forward the llama.cpp json_schema.
- **Per-request 10s timeout** via Ktor `timeout { requestTimeoutMillis = 10_000 }` on the call (overrides the shared 60s) so a slow cloud drops to local fast.
- Parse `choices[0].message.content`. Throw on: blank `OPENROUTER_KEY`, non-2xx, blank/missing content → the fallback fires. (Throwing, not returning null, because the `chat` contract is "return String or throw".)
- Reuse a dedicated request DTO (OpenRouter is OpenAI-compatible; `ChatCompletionResponse` already fits the response). A new `OpenRouterRequest` DTO carries `response_format` as `{"type":"json_object"}` and omits `temperature`/`stop` unless useful (Haiku is deterministic enough; send `temperature: 0` for parse stability).
- `health()` = `OPENROUTER_KEY.isNotBlank()` (cheap; no network ping needed — a real failure surfaces at `chat` time and falls back).

### 4.4 `FallbackChatClient` — `data/remote/FallbackChatClient.kt` (bound as `ChatClient`)
```kotlin
@Singleton
class FallbackChatClient @Inject constructor(
    private val cloud: CloudChatClient,
    private val local: LlmHttpClient,
) : ChatClient {
    private val _engine = MutableStateFlow(LlmEngineKind.NONE)
    override val engine = _engine.asStateFlow()

    override suspend fun chat(system, user, responseFormat): String {
        if (!BuildConfig.FORCE_LOCAL_LLM) {
            try { return cloud.chat(...).also { _engine.value = CLOUD } }
            catch (t: Throwable) { /* log, fall through */ }
        }
        return try { local.chat(...).also { _engine.value = ON_DEVICE } }
        catch (e: LlmUnavailableException) { _engine.value = NONE; throw e }
    }

    override suspend fun health() = (!BuildConfig.FORCE_LOCAL_LLM && cloud.health()) || local.health()
}
```
- Cloud-first; on **any** throwable from cloud (timeout / blank-key / HTTP error / blank body), fall back to local.
- If both fail → re-throw `LlmUnavailableException` (preserves the existing graceful-degradation contract in `IntentRouter`/`LlmEngine`, which catch it and surface manual entry).
- `FORCE_LOCAL_LLM` (debug BuildConfig, default `false`) short-circuits to local — for demoing the offline path on command.
- Updates `engine` so the UI badge reflects who answered.

### 4.5 Hilt wiring — `di/LlmBindModule.kt`
```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class LlmBindModule {
    @Binds @Singleton abstract fun bindChatClient(impl: FallbackChatClient): ChatClient
}
```
- `IntentRouter` + `LlmEngine`: change the constructor param type `LlmHttpClient` → `ChatClient` (one line each). No other change — `chat`/`health` signatures are identical.
- `CloudChatClient` and `LlmHttpClient` are concrete `@Inject`-constructed singletons; `FallbackChatClient` injects both.

### 4.6 What does NOT change in A
- All prompts (`SALE_SYSTEM_PROMPT`, `INTENT_SYSTEM_PROMPT`, `PAYMENT_*`, `CUSTOMER_NAME_*`) and `*_RESPONSE_FORMAT` builders — passed through unchanged; cloud just translates the response_format envelope.
- All parsers (`SaleParser`, `PaymentParser`, `parseIntent`, `parseCustomerName`) and `JsonParser.extractJson` — already tolerant of fenced/free-text JSON.
- Therefore §18, the Assistant flows (intent/sale/payment/customer/analytics), and edit-recent-entries ride along unchanged. **Re-verify after A.**

## 5. Architecture — Task B: Cloud OCR bill scan

```
BillScanScreen  (system camera via ActivityResultContracts.TakePicture + FileProvider, iQOO hardening)
   → JPEG file → ImageUtils.uriToBase64  (EXIF rotate, downscale longest side ≤1568, JPEG q90, Base64.NO_WRAP)
   → CloudVisionClient.extractBill(base64)  (OpenRouter vision model, data:image/jpeg;base64 URI, BILL_SYSTEM prompt, NO response_format)
   → ParsedBill(items: List<ParsedPurchaseItem>, total: Double?)
   → ScanConfirmSheet  (editable rows: name / qty / unit / unitPrice / amount)
   → confirm → LogPurchaseUseCase(items)
   → per item: resolve-or-create ItemEntity, incrementStock, refresh costPrice, insert PurchaseEntity
```

### 5.1 `CloudVisionClient` — `data/remote/CloudVisionClient.kt`
- Same OpenRouter endpoint, `model = BuildConfig.OPENROUTER_VISION_MODEL`, same auth headers.
- User turn = a text part (`BILL_USER`) + an `image_url` part `{"url":"data:image/jpeg;base64,$base64"}`. **Omit `response_format`** (not all vision models accept it).
- System prompt = `BILL_SYSTEM` (port the colleague's anti-hallucination prompt verbatim: "Read ONLY what is written … omit illegible lines rather than guess"). Schema: `{"items":[{name,qty,unit,unitPrice,amount}],"total":number|null}`.
- Parse with `JsonParser.extractJson` → kotlinx.serialization into a `ParsedBill` DTO. Throw on blank key / non-2xx / blank; the ViewModel surfaces an error state (no local vision fallback — cloud-only by decision).

### 5.2 `ImageUtils` — `util/ImageUtils.kt`
- Port the colleague's `uriToBase64`: `ImageDecoder`/`BitmapFactory`, EXIF rotate, downscale longest side ≤ `maxDim` (1568), `compress(JPEG, 90)`, `Base64.encodeToString(..., NO_WRAP)`.

### 5.3 Capture — `ui/scan/BillScanScreen.kt` + `BillScanViewModel.kt`
- System camera via `ActivityResultContracts.TakePicture()` + a `FileProvider` URI into the app cache dir.
- **iQOO hardening (port):** the camera may return `ok=false` on a successful capture and cold-restart the activity → gate ingestion on a `pending_uri` persisted in SharedPreferences, ingested via the result callback **and** `ON_RESUME` **and** a first-composition `LaunchedEffect`.
- Requires: `<provider android:name="androidx.core.content.FileProvider">` in the manifest + `res/xml/file_paths.xml` + a cache subdir. CAMERA permission already declared (`CLAUDE.md §14`); request at runtime via Accompanist.
- `VoiceState`-style sealed UI state: `Idle | Capturing | Reading | Result(ParsedBill) | Error(msg)`.

### 5.4 Domain — `domain/usecase/LogPurchaseUseCase.kt` + `PurchaseRepository`
- **`PurchaseRepository`** (interface in `domain/repository/`, impl in `data/repository/`) wrapping `PurchasesDao.insert` — keeps the use-case out of the DAO (project convention).
- **`ParsedPurchaseItem`** (domain model): `name, qty, unit, unitPrice?, amount?`.
- **`LogPurchaseUseCase(items, supplier?)`**: for each item →
  1. `inventory.findByName(name)` → if null, `addItem(ItemEntity(name, unit, costPrice = unitPrice ?: 0, ...))`; else use existing id.
  2. `inventory.incrementStock(id, qty)`.
  3. If `unitPrice != null && unitPrice > 0`, refresh `costPrice` via `updateItem` (latest purchase cost).
  4. `purchaseRepo.insert(PurchaseEntity(itemId, qty, cost = amount ?: (unitPrice?.times(qty) ?: 0.0), supplier, timestamp))`.
- All on `Dispatchers.IO`.

### 5.5 `ScanConfirmSheet` — `ui/scan/ScanConfirmSheet.kt`
- Editable list of parsed rows (name/qty/unit/unitPrice/amount), add/remove row, supplier field. Confirm → `LogPurchaseUseCase`. Reuse existing editable-field patterns where practical.

### 5.6 Entry point
- Surface "Scan bill" from the existing center-FAB sub-action / nav (the Assistant center-FAB already exists). Add a `scan` route to the NavHost.

## 6. Architecture — Task C: UI polish (light)

- **`Double.asRupees()`** — `util/CurrencyFormat.kt`: Indian digit grouping (`₹12,40,000`). Apply where amounts render (Home summary, P&L cards, Khata balances, confirm cards, scan sheet).
- **Engine badge** — a small composable chip reading `ChatClient.engine` (injected/observed in the relevant ViewModels): `☁ Cloud` / `📱 On-device` / hidden when `NONE`. Show on the Assistant result and Sale Entry result so the demo visibly shows which backend answered. **No glass theme.**

## 7. Cross-cutting

### 7.1 `build.gradle.kts` — keys → BuildConfig
```kotlin
val keysFile = rootProject.file("keys.properties")
val keys = Properties().apply { if (keysFile.exists()) keysFile.inputStream().use { load(it) } }
// in defaultConfig:
buildConfigField("String", "OPENROUTER_KEY", "\"${keys.getProperty("OPENROUTER_KEY", "")}\"")
buildConfigField("String", "OPENROUTER_MODEL", "\"${keys.getProperty("OPENROUTER_MODEL", "anthropic/claude-haiku-4.5")}\"")
buildConfigField("String", "OPENROUTER_VISION_MODEL", "\"${keys.getProperty("OPENROUTER_VISION_MODEL", "anthropic/claude-haiku-4.5")}\"")
buildConfigField("boolean", "FORCE_LOCAL_LLM", "false")
```
- Never print/echo/commit the key value. `keys.properties` stays gitignored. Empty default when the file is absent → cloud `health()` false → app runs local-only (CI/other machines don't break).

### 7.2 Testing (TDD where it pays)
- **`FallbackChatClient`** (unit, mockk): cloud success → returns cloud + `engine == CLOUD`; cloud throws → returns local + `engine == ON_DEVICE`; both throw → `LlmUnavailableException` + `engine == NONE`; `FORCE_LOCAL_LLM == true` → never calls cloud.
- **`CloudChatClient`** (unit): `responseFormat != null` → request body carries `{"type":"json_object"}` (not json_schema); `responseFormat == null` → no `response_format`; blank key → throws; happy path parses `choices[0].message.content`. Use Ktor `MockEngine`.
- **`ParsedBill` mapping** (unit): vision JSON (with fences / nulls / missing fields) → `ParsedBill` with sane defaults (qty default 1, unit default "pcs", null prices stay null).
- **`asRupees()`** (unit): Indian grouping cases (`1234 → ₹1,234`, `1240000 → ₹12,40,000`, decimals).
- Build-verify (`./gradlew :app:assembleDebug`) + on-device for camera/scan UI (can't unit-test capture).
- **Regression:** re-verify §18 (5/5) + the Assistant flows after A.

### 7.3 Docs
- Update `CLAUDE.md §1` framing (cloud-primary + local fallback preserves the offline story). Update `docs/STATUS.md` + `HANDOFF.md` after the build.

## 8. Build order & gate

**A → B → C.** Build A and B fully (and C is cheap, fold it in), then **pause for owner on-device verification** before considering the pivot done:
- A gate: all four text paths (sale/payment/intent/customer) parse via cloud on the iQOO; airplane mode / `FORCE_LOCAL_LLM` falls back to local; §18 still passes; Assistant flows intact; badge shows the right backend.
- B gate: snap a real supplier bill → items appear in the confirm sheet → confirm → inventory restocks + purchase logged.

## 9. Constraints / gotchas (carried)

- Toolchain pinned: AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3 / Room v3 `fallbackToDestructiveMigration`. Don't upgrade.
- Keep the local llama-server path intact as the fallback (`scripts/start-llama-server.sh`; `warmUpSale`/`warmUp` priming still helps the fallback).
- LLM (cloud or local) is parse/classify/vision only — analytics stay ordinary Room queries.
- Don't `adb uninstall` to reset (wipes the 181MB whisper model); use `adb shell run-as com.artha.kirana rm databases/artha.db*`.
- Device serial `10BFBG0CEL001DB`.

## 10. Out of scope

- ML Kit on-device OCR; handwritten-khata vision; local vision fallback (cloud bill only).
- Adopting the colleague's `ParsedTransaction` (EXPENSE/UPI) schema — keep our `SaleEntry`.
- Glass theme.
- Persistent tether-free llama-server start (a separate Phase 6 item).

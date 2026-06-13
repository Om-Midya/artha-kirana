# CLAUDE.md — Artha Kirana: AI-Powered Kirana Store Assistant
## Agent Instructions for Claude Code

---

## 0. How to Work (Read This First)

### Research before you build
Before implementing ANY library, feature, or API integration:
1. **Use Context7 MCP** to pull the latest docs for that library.
   - Command: `mcp__context7__resolve-library-id` then `mcp__context7__get-library-docs`
   - Do this for: LiteRT-LM, Room, CameraX, ML Kit, Hilt, Navigation Compose, Vico, Ktor
2. Confirm the current version from `libs.versions.toml` before writing any import
3. Never write code against a library you haven't verified the API for

### Ask when you need clarity — do not guess
Stop and ask the user when:
- A feature has multiple valid implementation approaches with real tradeoffs (e.g. "Should I store the model in assets or download it at first launch?")
- A permission or system API behaves differently across Android versions and you need to know the min SDK support level
- The PRD is ambiguous about a UX flow (e.g. "Does the confirmation screen for bill scanning allow editing individual line items?")
- You're about to make a breaking schema change to the Room database
- You need the Claude API key or any secret value

Format your question as:
```
QUESTION [feature-area]: <clear, specific question>
Options:
  A) ...
  B) ...
My recommendation: A, because...
```

### Build in phase order
Complete each phase fully before moving to the next. Each phase ends in a working, buildable, testable state. Never start Phase N+1 if Phase N doesn't compile.

### Never assume — verify on device
Always write code that degrades gracefully. Add TODO comments where device-specific behaviour (OriginOS, Snapdragon 8 Elite Gen 5) may differ from standard Android.

---

## 1. Project Context

**App:** Artha Kirana — a local-first, AI-powered business assistant for kirana store owners in India.

**Tagline:** The iQOO 15 becomes the shop's accountant, cashier, and business analyst.

**Core loop:**
```
Shopkeeper speaks/types a sale in Hindi
        ↓
On-device LLM (LiteRT-LM + Gemma 3 1B) parses it into structured JSON
        ↓
Room DB: sales ledger + inventory update + khata balance
        ↓
Vernacular summary shown/spoken back
All offline. Nothing leaves the device (except anonymised market trend request).
```

**Hackathon device:** iQOO 15 (Snapdragon 8 Elite Gen 5, up to 16GB RAM, UFS 4.1)
**Target audience:** Kirana store owners in Tier 2/3 India, Hindi/Kannada/Tamil speakers
**Build constraint:** ~30 hours, phone-first, offline-first

---

## 2. Tech Stack (Final Decisions)

### Language & UI
- **Kotlin** (no Java)
- **Jetpack Compose** — all UI (no XML layouts)
- **Material 3** — design system

### Architecture
- **Clean Architecture** with three layers:
  - `data/` — Room DB, repositories, DTOs
  - `domain/` — Use cases, domain models, repository interfaces
  - `ui/` — Composables, ViewModels, UI state
- **MVVM** — ViewModel + StateFlow + Compose collectAsStateWithLifecycle
- **Single Activity** with Navigation Compose

### On-Device LLM (MOST IMPORTANT — look up via Context7 first)
- **LiteRT-LM (Kotlin API)** — Google's recommended on-device LLM runtime (replaces MediaPipe LLM API which is now maintenance-only)
- Docs: https://ai.google.dev/edge/litert-lm/overview
- Model: **Gemma 3 1B** (`.litertlm` format from HuggingFace LiteRT Community)
- Fallback: MediaPipe `tasks-genai` if LiteRT-LM Kotlin API isn't stable yet (ask user)
- **Use Context7 to pull LiteRT-LM Kotlin API docs before writing any LLM code**

### OCR (Bill Scanning)
- **ML Kit Text Recognition** — `com.google.mlkit:text-recognition`
- Runs fully on-device, no cloud, supports Latin + Devanagari scripts
- Use Context7 for latest API

### Camera
- **CameraX** — `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`
- Use Context7 for latest CameraX Compose integration

### Database
- **Room** with **SQLCipher** for encryption
- `androidx.room:room-runtime`, `androidx.room:room-ktx`
- `net.zetetic:android-database-sqlcipher`
- Use Context7 for Room + SQLCipher integration pattern

### Dependency Injection
- **Hilt** — `com.google.dagger:hilt-android`

### Navigation
- **Navigation Compose** — `androidx.navigation:navigation-compose`

### Voice Input
- **Android SpeechRecognizer** (built-in, no library needed)
- Hindi locale: `Locale("hi", "IN")`
- Verify offline capability in first 2 hours on the actual iQOO device

### Voice Output
- **Android TextToSpeech** (built-in)
- Set language to `Locale("hi", "IN")` for Hindi output

### Background / Alerts
- **WorkManager** — for inventory threshold monitoring
- **NotificationManager** — local push notifications

### Charts (P&L Dashboard)
- **Vico** — `com.patrykandpatrick.vico:compose` — Compose-native charts
- Use Context7 for latest Vico API

### HTTP (Claude API for Market Trends)
- **Ktor** — `io.ktor:ktor-client-android`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`
- **kotlinx.serialization** — `org.jetbrains.kotlinx:kotlinx-serialization-json`

### Permissions
- **Accompanist Permissions** — `com.google.accompanist:accompanist-permissions`

### Other Utilities
- **Coil** — image loading in Compose (`io.coil-kt:coil-compose`)
- **DataStore** — for app preferences (`androidx.datastore:datastore-preferences`)
- **Timber** — logging (`com.jakewharton.timber:timber`)

---

## 3. Package Structure

```
com.artha.kirana/
├── di/                          # Hilt modules
│   ├── DatabaseModule.kt
│   ├── LlmModule.kt
│   ├── RepositoryModule.kt
│   └── NetworkModule.kt
│
├── data/
│   ├── db/
│   │   ├── ArthaDatabase.kt     # Room DB + SQLCipher setup
│   │   ├── dao/
│   │   │   ├── SalesDao.kt
│   │   │   ├── ItemsDao.kt
│   │   │   ├── PurchasesDao.kt
│   │   │   ├── KhataDao.kt
│   │   │   └── KhataTransactionDao.kt
│   │   └── entity/
│   │       ├── SaleEntity.kt
│   │       ├── ItemEntity.kt
│   │       ├── PurchaseEntity.kt
│   │       ├── KhataEntity.kt
│   │       └── KhataTransactionEntity.kt
│   ├── repository/
│   │   ├── SalesRepositoryImpl.kt
│   │   ├── InventoryRepositoryImpl.kt
│   │   ├── KhataRepositoryImpl.kt
│   │   └── InsightsRepositoryImpl.kt
│   ├── llm/
│   │   ├── LlmEngine.kt         # LiteRT-LM wrapper
│   │   ├── SaleParser.kt        # Parses sale text → SaleEntry
│   │   └── BillParser.kt        # Parses OCR text → PurchaseEntries
│   ├── ocr/
│   │   └── BillOcrEngine.kt     # ML Kit Text Recognition wrapper
│   ├── voice/
│   │   ├── SpeechRecognizerManager.kt
│   │   └── TextToSpeechManager.kt
│   ├── remote/
│   │   └── ClaudeApiClient.kt   # Ktor client for market trends
│   └── worker/
│       └── InventoryAlertWorker.kt  # WorkManager background check
│
├── domain/
│   ├── model/
│   │   ├── Sale.kt
│   │   ├── Item.kt
│   │   ├── Purchase.kt
│   │   ├── KhataEntry.kt
│   │   ├── SaleEntry.kt         # Parsed LLM output
│   │   └── PnlSummary.kt
│   ├── repository/
│   │   ├── SalesRepository.kt
│   │   ├── InventoryRepository.kt
│   │   ├── KhataRepository.kt
│   │   └── InsightsRepository.kt
│   └── usecase/
│       ├── ParseSaleEntryUseCase.kt
│       ├── LogSaleUseCase.kt
│       ├── LogPurchaseUseCase.kt
│       ├── GetPnlSummaryUseCase.kt
│       ├── GetKhataListUseCase.kt
│       ├── ScanBillUseCase.kt
│       └── GetMarketInsightsUseCase.kt
│
├── ui/
│   ├── MainActivity.kt
│   ├── ArthaApp.kt              # NavHost + bottom nav
│   ├── theme/
│   │   ├── Color.kt             # Brand colors
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── home/
│   │   ├── HomeScreen.kt        # Today's summary + quick entry
│   │   └── HomeViewModel.kt
│   ├── entry/
│   │   ├── SaleEntryScreen.kt   # Typed + voice entry
│   │   ├── SaleEntryViewModel.kt
│   │   └── VoiceEntryButton.kt  # Mic FAB with animation
│   ├── inventory/
│   │   ├── InventoryScreen.kt
│   │   ├── InventoryViewModel.kt
│   │   ├── AddItemSheet.kt      # Bottom sheet for new item
│   │   └── ItemCard.kt
│   ├── khata/
│   │   ├── KhataScreen.kt
│   │   ├── KhataViewModel.kt
│   │   └── KhataPartyDetail.kt
│   ├── pnl/
│   │   ├── PnlScreen.kt
│   │   ├── PnlViewModel.kt
│   │   └── ProfitChart.kt       # Vico chart
│   ├── scan/
│   │   ├── BillScanScreen.kt    # CameraX + ML Kit
│   │   ├── BillScanViewModel.kt
│   │   └── ScanConfirmSheet.kt  # Review parsed entries
│   └── insights/
│       ├── InsightsScreen.kt
│       └── InsightsViewModel.kt
│
└── util/
    ├── JsonParser.kt            # Robust JSON extraction from LLM output
    ├── PermissionHelper.kt
    └── Extensions.kt
```

---

## 4. Database Schema (Room Entities)

### ItemEntity
```kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameHi: String? = null,        // Hindi display name
    val qtyInStock: Double = 0.0,
    val unit: String = "piece",        // kg / litre / piece / dozen
    val costPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val reorderThreshold: Double = 0.0,
    val category: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

### SaleEntity
```kotlin
@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long? = null,          // null for pure khata/repayment entries
    val qtySold: Double = 0.0,
    val amount: Double,
    val type: String,                  // "cash" | "credit" | "repayment"
    val party: String? = null,
    val inputMethod: String,           // "voice" | "scan" | "typed"
    val rawInput: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

### PurchaseEntity
```kotlin
@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val qty: Double,
    val cost: Double,
    val supplier: String? = null,
    val billScanUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

### KhataEntity
```kotlin
@Entity(tableName = "khata")
data class KhataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyName: String,
    val balance: Double = 0.0,         // positive = they owe us
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### KhataTransactionEntity
```kotlin
@Entity(tableName = "khata_transactions",
    foreignKeys = [
        ForeignKey(entity = KhataEntity::class, parentColumns = ["id"], childColumns = ["partyId"]),
        ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.SET_NULL)
    ])
data class KhataTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyId: Long,
    val amount: Double,
    val type: String,                  // "credit" | "repayment"
    val saleId: Long? = null,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## 5. LLM Prompt Specifications

### SALE ENTRY PARSER
```
System prompt (verbatim — do not modify):
You are a kirana store billing assistant. Parse the input into JSON only.
Return ONLY the JSON object. No explanation. No markdown. No preamble.
No "here is the JSON". Just the raw JSON object.

Schema:
{"entries":[{"item":string|null,"qty":string|null,"amount":number|null,"type":"cash"|"credit"|"repayment","party":string|null}]}

Rules:
- type defaults to "cash" if not mentioned
- "उधार" or "udhaar" means type = "credit"
- "दिए" or "ne diya" means type = "repayment"
- Multiple items in one sentence = multiple entries in the array
- Return ONLY valid JSON. Nothing else.
```

**LLM config:** temperature=0.1, maxTokens=256, stopSequences=["```", "\n\n"]

### BILL OCR PARSER
```
System prompt:
You are a kirana store assistant. Below is OCR-extracted text from a supplier bill.
Extract all line items into JSON only. No explanation. No markdown.

Schema:
{"supplier":string|null,"total":number|null,"items":[{"name":string,"qty":string,"unitPrice":number,"total":number}]}

Return ONLY valid JSON.
```

### MARKET TRENDS (Claude API — cloud)
```
System prompt:
You are a business analyst for a small kirana store in India.
Here is anonymised sales data for the past 7 days (item names + quantities only — no personal data):
[SALES_DATA_JSON]

Give 3-5 specific, actionable insights. Examples of good insights:
- "Your rice sales spike on Fridays — stock up on Thursdays"
- "Parle-G has your highest volume but your margin may be low"
- "A seasonal trend may be starting for [item]"

Respond in simple Hindi and English mixed (Hinglish).
No jargon. Be specific. Under 150 words total.
```

---

## 6. LLM JSON Extraction Safety

The LLM may return markdown fences, preamble text, or trailing content despite the system prompt.
Always extract JSON safely:

```kotlin
// util/JsonParser.kt
object JsonParser {
    fun extractJson(raw: String): String? {
        // Strip markdown fences
        val stripped = raw
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        // Find first { and last }
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
    }
}
```

Always wrap LLM parse calls in try-catch. On failure, show the user a manual entry screen — never crash.

---

## 7. Key LiteRT-LM Implementation Notes

**IMPORTANT: Use Context7 to get the latest LiteRT-LM Kotlin API docs before writing this.**
The API changed recently (MediaPipe deprecated). Key things to verify:
- Correct Gradle dependency (may be `com.google.ai.edge.litert:litert-lm` or similar)
- Model file format (`.litertlm` vs `.task`)
- Session management (create once, reuse)
- Async inference (use Kotlin Flow or suspend functions)
- GPU/NPU acceleration flags for Snapdragon

The model file (~2GB) should be:
- Downloaded to `filesDir` at first launch (not bundled in APK)
- Loaded once in `LlmModule.kt` as a singleton
- Kept in memory — do not reload per request

Ask the user: "Should I download the model file on first app launch from HuggingFace, or will you push it via ADB? This affects the first-launch UX."

---

## 8. Voice Input Implementation

```kotlin
// data/voice/SpeechRecognizerManager.kt
// Use Android SpeechRecognizer with LANGUAGE_MODEL_FREE_FORM
// Set:
//   RecognizerIntent.EXTRA_LANGUAGE = "hi-IN"
//   RecognizerIntent.EXTRA_PREFER_OFFLINE = true  ← CRITICAL for hackathon demo
//   RecognizerIntent.EXTRA_MAX_RESULTS = 1

// IMPORTANT: Test offline mode in H0-2 on the actual iQOO before building the full flow
// If offline STT fails, fall back to typed entry silently
// Emit a SpeechState sealed class: Listening | Result(text) | Error | Offline
```

---

## 9. Bill Scanning Flow

```
CameraX preview (composable) → capture → ImageProxy
        ↓
ML Kit TextRecognizer.process(inputImage)
        ↓
raw OCR text → LlmEngine.parseBill(text)
        ↓
List<ParsedItem> shown in ScanConfirmSheet (editable)
        ↓
User confirms → LogPurchaseUseCase → inventory updated
```

**Use Context7 for CameraX + Compose integration (ImageAnalysis vs capture)**

---

## 10. P&L Calculation (No stored table — computed from Room queries)

```kotlin
// domain/usecase/GetPnlSummaryUseCase.kt
data class PnlSummary(
    val grossRevenue: Double,
    val cogs: Double,
    val grossProfit: Double,
    val cashCollected: Double,
    val totalOutstanding: Double,
    val period: PnlPeriod  // TODAY | THIS_WEEK | THIS_MONTH
)

// Room queries needed:
// SalesDao: SUM(amount) WHERE type != 'repayment' AND timestamp BETWEEN start AND end
// SalesDao: JOIN items — SUM(qty_sold * cost_price) for COGS
// KhataDao: SUM(balance) WHERE balance > 0
// SalesDao: SUM(amount) WHERE type = 'cash' AND timestamp BETWEEN start AND end
```

---

## 11. Inventory Alert Worker

```kotlin
// data/worker/InventoryAlertWorker.kt
// Runs every 30 minutes via PeriodicWorkRequest
// Queries: SELECT * FROM items WHERE qty_in_stock < reorder_threshold AND reorder_threshold > 0
// For each item below threshold: fire NotificationManager with channel INVENTORY_ALERTS
// IMPORTANT: OriginOS battery optimization may kill WorkManager. Document this in UI settings screen.
// Add a "Fix battery settings" deep link → Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
```

---

## 12. Market Trends (Claude API)

```kotlin
// data/remote/ClaudeApiClient.kt — Ktor + kotlinx.serialization
// Endpoint: https://api.anthropic.com/v1/messages
// Model: claude-sonnet-4-6
// Headers: x-api-key, anthropic-version: 2023-06-01
// Payload: ONLY anonymised item-level aggregates (item name + qty sold)
// NEVER include: party names, balances, amounts, timestamps

// Cache the last response in DataStore — re-use if < 6 hours old
// This avoids API calls during the demo and handles offline gracefully
```

---

## 13. UI/UX Specs

### Brand Colors (theme/Color.kt)
```kotlin
val BrandGold    = Color(0xFFE6AC00)
val BrandDark    = Color(0xFF1A1A1A)
val AccentGreen  = Color(0xFF2D7D46)
val AccentRed    = Color(0xFFC0392B)
val AccentBlue   = Color(0xFF1A5276)
val SurfaceDark  = Color(0xFF1E1E1E)
```

### Bottom Navigation (4 tabs)
```
🏠 Home (today's summary + quick entry FAB)
📦 Inventory (stock list + low-stock alerts)
📒 Khata (credit ledger by party)
📊 P&L (profit dashboard + charts)
```

### Floating Action Button (Home screen)
```
Central FAB with two sub-actions (expandable):
  🎙️ Voice entry
  📷 Scan bill
Long press or type for text entry (default)
```

### Entry Confirmation Screen
```
After LLM parses a sale, show a confirmation card:
- Parsed fields (item, qty, amount, type, party)
- Edit button for any field
- Confirm / Cancel
- If JSON parse failed: show raw text + manual entry form
```

### Vernacular Toggle
```
Settings screen: Output language selector
  • Hindi (हिंदी)    ← default
  • Kannada (ಕನ್ನಡ)
  • English
Applies to: summaries, insights cards, TTS output
```

---

## 14. Permissions Required

Add to AndroidManifest.xml:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
```

Request at runtime using Accompanist Permissions. Request RECORD_AUDIO before showing voice button. Request CAMERA before opening scanner.

---

## 15. Implementation Phases

### Phase 0 — Project Foundation (H0–2)
**Goal:** The project builds, runs on the iQOO, DB layer works, and all three hardware
assumptions are verified before a single feature line is written.

**Ask before starting:**
```
QUESTION [model-delivery]: How should the app get the Qwen2.5 3B model file?
Options:
  A) Push via ADB before demo: adb push model.gguf /sdcard/artha/model.gguf  (faster for hackathon)
  B) Download on first launch from HuggingFace  (cleaner UX for judges)
My recommendation: A for hackathon speed, B for judge impression. Your call.
```

Tasks:
1. Set up `libs.versions.toml` with all dependencies (use Context7 to verify latest versions)
2. Configure `build.gradle.kts` — Hilt plugin, Room KSP, serialization plugin
3. Set up Hilt — `@HiltAndroidApp` on `ArthaApplication`
4. Implement Room DB with SQLCipher (`ArthaDatabase.kt`) with all 5 entities and DAOs
5. Implement Hilt `DatabaseModule` and `RepositoryModule`
6. Implement all 5 repository interfaces and impls (stub implementations)
7. Set up Navigation Compose with 4 tabs
8. Set up brand theme (Color.kt, Type.kt, Theme.kt)

Then run these three verification spikes IN ORDER before any feature code:

---

#### SPIKE A — LLM Backend Verification (run once model is loaded)

Add a temporary `DebugViewModel` or run inline from `MainActivity.onCreate`:

```kotlin
// Run this once in Phase 0, remove before Phase 6 demo hardening
private fun verifyLlmBackend() {
    lifecycleScope.launch(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = llmEngine.generate("Say: OK")
        val elapsed = System.currentTimeMillis() - start

        Log.d("ARTHA_SPIKE", "=== LLM BACKEND VERIFY ===")
        Log.d("ARTHA_SPIKE", "First inference: ${elapsed}ms | Output: $result")

        // Interpret:
        // elapsed < 2000ms  → GPU backend (Adreno 840) ✅
        // elapsed 2000-5000ms → GPU backend, cold start ✅
        // elapsed > 8000ms  → CPU fallback ⚠️ — force GPU delegate via Context7 docs

        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity,
                "LLM: ${elapsed}ms", Toast.LENGTH_LONG).show()
        }
    }
}
```

**Pass:** Toast shows in < 5 seconds. Logcat shows output text.
**Fail:** Crash or timeout → check model path and LiteRT-LM init params via Context7.

---

#### SPIKE B — Hindi Offline STT Verification

```kotlin
// Temporary spike in MainActivity — remove after Phase 0 passes
private val sttLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    val text = result.data
        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        ?.firstOrNull() ?: "NO RESULT"
    Log.d("ARTHA_SPIKE", "STT: $text")
    Toast.makeText(this, "STT: $text", Toast.LENGTH_LONG).show()
}

private fun verifyHindiStt() {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                 RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)  // ← CRITICAL
        putExtra(RecognizerIntent.EXTRA_PROMPT, "हिंदी में बोलें")
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    sttLauncher.launch(intent)
}
```

**Test:** Turn airplane mode ON → call `verifyHindiStt()` → speak "दो किलो चावल"

**Pass:** Toast shows Hindi text → implement full voice flow in Phase 4.
**Fail:** Error or empty → set `VOICE_ENABLED = false` in BuildConfig. Phase 4 shows
"Voice coming soon" UI. Do NOT debug STT during the hackathon — typed entry covers it.

---

#### SPIKE C — OriginOS Battery Optimisation (ADB from Mac, run once)

```bash
# Run with phone connected via USB or Wi-Fi ADB
# Re-run before every demo session (resets on restart)

adb shell device_config put activity_manager max_phantom_processes 2147483647
adb shell dumpsys deviceidle whitelist +com.artha.kirana
adb shell cmd appops set com.artha.kirana RUN_IN_BACKGROUND allow
adb shell cmd appops set com.artha.kirana RUN_ANY_IN_BACKGROUND allow

# Verify it worked:
adb shell dumpsys jobscheduler | grep "com.artha.kirana"
# Must show jobs — if empty, WorkManager alerts will NOT fire
```

Manual fallback on device:
```
Settings → Apps → Artha Kirana → Battery → Unrestricted
Settings → Apps → Artha Kirana → Auto-start → ON
```

**Pass:** `dumpsys jobscheduler` shows your package. Inventory alerts survive screen-off.
**Fail:** Re-run ADB commands. If still failing, add the manual instructions to onboarding.

---

**Phase 0 Checkpoint:** All three spikes pass. App launches. DB initialises. Navigation works.
Model loads and returns a token. Record the SPIKE A elapsed time as a comment in `LlmEngine.kt`.

---

### Phase 1 — Core Sale Entry Loop (H2–8)
**Goal:** Type a Hindi sale sentence → LLM parses it → entry in DB → home screen shows it.

Tasks:
1. Implement `LlmEngine.kt` — singleton, LiteRT-LM wrapper with sale parser system prompt
2. Implement `JsonParser.kt` — safe JSON extraction with fallback
3. Implement `SaleEntry` domain model and `ParseSaleEntryUseCase`
4. Implement `LogSaleUseCase` — writes sale, updates item stock, updates khata balance
5. Build `SaleEntryScreen` — text input, parse button, confirmation card
6. Build `HomeScreen` — today's total, recent entries list
7. Build `HomeViewModel` with StateFlow
8. Wire `LogSaleUseCase` through ViewModel to screen

**Validation:** Run all 5 test cases from PRD Section 8 test cases. >90% must parse correctly.

**Checkpoint:** Type "दो किलो चावल अस्सी रुपये उधार रमेश" → confirmation card → DB entry → home screen updates.

---

### Phase 2 — Inventory + Khata + P&L (H8–14)
**Goal:** Complete shop management loop — sale in, stock down, khata updated, profit shown.

Tasks:
1. Build `InventoryScreen` — item list with stock levels, low-stock highlighting
2. Build `AddItemSheet` — bottom sheet for adding new items with cost/sell price/threshold
3. Implement `InventoryAlertWorker` — WorkManager periodic check, notification on low stock
4. Build `KhataScreen` — party list with outstanding balances
5. Build `KhataPartyDetail` — transaction history for one party
6. Implement `GetPnlSummaryUseCase` with Room queries
7. Build `PnlScreen` — today/week/month tabs, gross revenue/cost/profit cards
8. Add `ProfitChart` using Vico — daily revenue bar chart for the week
9. Wire all ViewModels

**Ask if needed:** "Does the low-stock notification need to go away once the user restocks, or should it persist until dismissed?"

**Checkpoint:** Log 10 varied sales → inventory counts correctly → khata shows correct balances → P&L arithmetic verified.

---

### Phase 3 — Bill Scanning (H14–18)
**Goal:** Snap a supplier bill → ML Kit reads it → LLM parses items → inventory updated.

Tasks:
1. Add CameraX composable to `BillScanScreen` — use Context7 for latest Compose CameraX API
2. Implement image capture → `BillOcrEngine` (ML Kit TextRecognizer)
3. Implement `BillParser` — OCR text → LLM with bill-parsing system prompt
4. Build `ScanConfirmSheet` — review parsed line items, allow editing
5. Wire `LogPurchaseUseCase` on confirm → inventory updated + purchase cost logged

**Ask if needed:** "The OCR + LLM chain takes 3-6 seconds. Should I show a loading skeleton or a progress indicator?"

**Checkpoint:** Snap any printed receipt → items appear in confirmation sheet → confirm → inventory increases.

---

### Phase 4 — Voice Input & Vernacular Output (H18–22)
**Goal:** Speak a sale in Hindi → parsed → logged. Daily summary spoken back.

Tasks:
1. Implement `SpeechRecognizerManager` — offline Hindi STT, sealed state class
2. Add `VoiceEntryButton` FAB with listening animation (use Compose animate APIs)
3. Wire voice text into existing `ParseSaleEntryUseCase` (same pipeline as typed)
4. Implement `TextToSpeechManager` — Hindi TTS singleton
5. Add "Hear summary" button on Home screen → TTS reads today's P&L in Hindi
6. Add vernacular language selector in settings

**If voice STT fails offline:** Replace voice button with a "Coming soon" state and log a TODO. Do not block the demo.

**Checkpoint:** Speak "तीन साबुन बीस-बीस के" → parsed correctly → logged. TTS reads "आज की बिक्री..." on tap.

---

### Phase 5 — Market Trends Insights (H22–25)
**Goal:** Claude API generates 3-5 insights from anonymised sales history.

Tasks:
1. Implement `ClaudeApiClient` with Ktor — POST to /v1/messages
2. Build anonymised sales aggregation query (item name + qty only — no amounts, no parties)
3. Implement `GetMarketInsightsUseCase` — calls Claude API, caches in DataStore for 6 hours
4. Build `InsightsScreen` — insight cards in Hindi/English
5. Add Insights tab to bottom nav

**Ask before implementing:** "Do you have a Claude API key ready to inject? Where should it be stored — hardcoded for hackathon or in a local.properties file?"

**Checkpoint:** Insights screen shows 3 cards in Hinglish based on seeded sales data. Works on cached response when offline.

---

### Phase 6 — Demo Hardening (H25–30)
**Goal:** Zero crashes in 3 full demo runs. Airplane mode. Real seeded data.

Tasks:
1. Seed realistic demo data — `DemoDataSeeder.kt` called once on first launch in debug builds
   - 15 sales across 5 items (rice, sugar, oil, soap, biscuits)
   - 3 credit entries for "Ramesh", "Priya", "Suresh"
   - 2 purchases (restocks)
   - One item near reorder threshold
2. Add "Fix battery optimisation" setting — deep link to OriginOS power settings
3. Test all 5 LLM test cases — fix any failures
4. Test bill scanning with 3 different real receipt photos
5. Test voice entry offline (airplane mode) — verify graceful fallback
6. Fix any keyboard/back navigation issues
7. Add error states everywhere — no empty screens, no crashes on null data
8. Run 3 complete demo walkthroughs matching the 2-minute script from PRD

**DEMO SCRIPT (burn this into memory):**
1. Airplane mode ON (show judges)
2. Type/speak sale in Hindi → parsed → ledger
3. Credit entry for Ramesh → khata updates
4. Snap a bill → items confirmed → inventory up
5. P&L tab → "₹X gross profit today"
6. Low-stock notification fires
7. Network ON → Insights tab → 3 Claude insights
8. Close: "Everything except the last screen ran on the iQOO. Nothing left this phone."

---

## 16. Coding Standards

- **Kotlin idioms only** — no Java-style nullability, use `?.` and `?:`
- **No hardcoded strings** — all UI text in `strings.xml` with Hindi variants in `values-hi/strings.xml`
- **StateFlow everywhere** — no LiveData, no callbacks in UI layer
- **`collectAsStateWithLifecycle`** — not `collectAsState` (lifecycle-aware)
- **Coroutines only** — no threads, no AsyncTask, no RxJava
- **Hilt for everything** — no manual DI, no singletons outside Hilt modules
- **Sealed classes for UI state:**
  ```kotlin
  sealed class UiState<out T> {
      object Loading : UiState<Nothing>()
      data class Success<T>(val data: T) : UiState<T>()
      data class Error(val message: String) : UiState<Nothing>()
  }
  ```
- **Never call LLM on main thread** — always `withContext(Dispatchers.IO)`
- **Room queries return Flow** — let the UI react to DB changes automatically

---

## 17. Things NOT to Build in 30 Hours

Do not implement, do not mention to user as in scope:
- WhatsApp Business API
- UPI QR code generation
- GST calculation
- Multi-shop support
- Supplier auto-ordering
- ONDC integration
- User authentication / login
- Cloud sync or backup

If asked about any of these: "That's on the roadmap after the hackathon — out of scope for the 30-hour build."

---

## 18. Quick Reference — LLM Test Cases

Always validate these before demo:
| Input | Expected type | Expected party |
|---|---|---|
| "दो किलो चावल, अस्सी रुपये, उधार रमेश को" | credit | Ramesh |
| "2 kilo cheeni forty rupees" | cash | null |
| "रमेश ने पचास रुपये दिए" | repayment | Ramesh |
| "teen soap bees-bees ke credit Priya" | credit | Priya |
| "chawal aur daal kul 120" | cash | null |

If any test case fails: adjust the system prompt temperature/stop-tokens first. If still failing: check Context7 for LiteRT-LM inference parameter docs.

---

*Artha Kirana · CLAUDE.md · iQOO Hackathon 2026 · v1.0*

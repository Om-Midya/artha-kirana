# CLAUDE.md — Artha Kirana: AI-Powered Kirana Store Assistant
## Agent Instructions for Claude Code

---

## 0. How to Work (Read This First)

### Research before you build
Before implementing ANY library, feature, or API integration:
1. **Use Context7 MCP** to pull the latest docs for that library.
    - Command: `mcp__context7__resolve-library-id` then `mcp__context7__get-library-docs`
    - Do this for: whisper.cpp (Android JNI), Room, CameraX, ML Kit, Hilt, Navigation Compose, Vico, Ktor
2. Confirm the current version from `libs.versions.toml` before writing any import
3. Never write code against a library you haven't verified the API for

### Ask when you need clarity — do not guess
Stop and ask the user when:
- A feature has multiple valid implementation approaches with real tradeoffs
- A permission or system API behaves differently across Android versions
- The spec is ambiguous about a UX flow
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
On-device LLM (Qwen 2.5 3B) parses it into structured JSON
        ↓
Room DB: sales ledger + inventory update + khata balance
        ↓
Vernacular summary shown/spoken back
All offline. Nothing leaves the device (except anonymised market trend request).
```

### ARCHITECTURE NOTE — LLM runtime (current deviation from original spec)
The LLM currently runs via **llama-server (Qwen 2.5 3B) over `127.0.0.1:8080`**, NOT in-app
LiteRT/JNI. This was a pragmatic dev-speed decision.

**HARD REQUIREMENT: llama-server MUST run ON THE iQOO itself** (Termux or native Android
process), NEVER on a tethered Mac with ADB port-forwarding. The entire pitch is
"nothing leaves the phone." If the server runs on a laptop:
- Airplane mode breaks the demo
- The core privacy claim becomes false
- A judge asking "where does the model run?" collapses the story

**Verification gate (must pass before Phase 6 demo):** Enable airplane mode, disconnect
any Mac/USB tether, and confirm a sale still parses end-to-end. If it doesn't, completing
the on-device server setup is the #1 priority — above all features. Document the exact
untethered start procedure in `docs/demo-runbook.md`.

Acceptable long-term alternative: migrate to in-app inference (LiteRT-LM or llama.cpp JNI)
so the model loads inside the app process. Only do this if time allows after core features.

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

### On-Device LLM
- **Qwen 2.5 3B** via **llama-server** over `127.0.0.1:8080` (see ARCHITECTURE NOTE in §1)
- Config: temperature=0.1, maxTokens=256, stopSequences=["```", "\n\n"]
- MUST run on-device. Verify via airplane-mode test before demo.
- Sale parser system prompt: see §5. Keep `LlmEngine.SALE_SYSTEM_PROMPT` in sync with
  any external validation script (e.g. `validate-sale-prompt.py`).

### Voice Input
- **whisper.cpp** — on-device ASR via JNI (NOT Android SpeechRecognizer)
- Rationale: removes dependency on OriginOS allowing system STT offline; fully
  self-contained and guaranteed offline, reinforcing the "nothing leaves the phone" pitch
- Model: ggml-tiny or ggml-base (multilingual, ~40–75MB). Test tiny first; use base/small
  only if Hindi accuracy is poor
- Reference impls: `ggml-org/whisper.cpp` (has an Android Compose example),
  `mikeesto/whispercpp-android`
- Flow: record 16kHz mono WAV → whisper.cpp transcribe → feed text into the existing
  ParseSaleEntryUseCase (same pipeline as typed input)
- Use Context7 for whisper.cpp Android JNI build setup before implementing
- Fallback chain if native build is too costly in hackathon time:
  (1) Android SpeechRecognizer with EXTRA_PREFER_OFFLINE=true, or (2) typed-entry only

### Voice Output
- **Android TextToSpeech** (built-in)
- Set language to `Locale("hi", "IN")` for Hindi output

### OCR (Bill Scanning)
- **ML Kit Text Recognition** — `com.google.mlkit:text-recognition`
- Runs fully on-device, no cloud, supports Latin + Devanagari scripts
- Use Context7 for latest API

### Camera
- **CameraX** — `androidx.camera:camera-camera2`, `camera-lifecycle`, `camera-view`
- Use Context7 for latest CameraX Compose integration

### Database
- **Room** with **SQLCipher** for encryption (SQLCipher currently DEFERRED behind a
  swap-in seam in `DatabaseModule` — enable when time allows)
- `androidx.room:room-runtime`, `androidx.room:room-ktx`
- `net.zetetic:android-database-sqlcipher`
- Use Context7 for Room + SQLCipher integration pattern

### Dependency Injection
- **Hilt** — `com.google.dagger:hilt-android`

### Navigation
- **Navigation Compose** — `androidx.navigation:navigation-compose`

### Background / Alerts
- **WorkManager** — for inventory threshold monitoring
- **NotificationManager** — local push notifications

### Charts (P&L Dashboard)
- **Vico** — `com.patrykandpatrick.vico:compose` — Compose-native charts
- NOTE: Vico 3.1.0 needs Kotlin 2.3.x; pick a version compatible with the project's
  Kotlin (currently 2.0.x). Verify via Context7 before adding.

### HTTP (llama-server + Claude API)
- **Ktor** — `io.ktor:ktor-client-android`, `ktor-client-content-negotiation`,
  `ktor-serialization-kotlinx-json`
- Used for both the local llama-server (127.0.0.1:8080) and the Claude API (market trends)
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
│   ├── DatabaseModule.kt        # Room (SQLCipher seam deferred)
│   ├── LlmModule.kt             # llama-server HTTP client wiring
│   ├── VoiceModule.kt           # WhisperEngine singleton
│   ├── RepositoryModule.kt
│   └── NetworkModule.kt
│
├── data/
│   ├── db/
│   │   ├── ArthaDatabase.kt     # Room DB (version 3; customers table; sales/khata customerId FKs; price snapshots)
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
│   │   ├── LlmEngine.kt         # llama-server HTTP wrapper; holds SALE_SYSTEM_PROMPT
│   │   ├── SaleParser.kt        # Parses sale text → SaleEntry
│   │   └── BillParser.kt        # Parses OCR text → PurchaseEntries
│   ├── ocr/
│   │   └── BillOcrEngine.kt     # ML Kit Text Recognition wrapper
│   ├── voice/
│   │   ├── WhisperEngine.kt     # whisper.cpp JNI wrapper, transcribe WAV → text
│   │   ├── AudioRecorder.kt     # captures 16kHz mono PCM/WAV
│   │   └── TextToSpeechManager.kt  # Android TTS for Hindi output
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
│   │   └── VoiceEntryButton.kt  # Mic FAB with recording animation
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

NOTE: Current DB is **version 3** — `itemName` denormalized onto sales; **customers are
first-class** (`customers` table; `sales.customerId` + `khata.customerId` FKs); sales snapshot
`unitPrice`/`unitCost` per line for drift-free margins; indices on `sales` timestamp/itemId/customerId.
`fallbackToDestructiveMigration` is enabled for hackathon speed (a schema bump WIPES dev data).

### CustomerEntity (DB v3 — identity hub)
```kotlin
@Entity(tableName = "customers", indices = [Index(value = ["name"], unique = true)])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                  // looked up COLLATE NOCASE via CustomerRepository.resolveOrCreate
    val nameHi: String? = null,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

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

### SaleEntity (DB v3)
```kotlin
@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("timestamp"), Index("itemId"), Index("customerId")]
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long? = null,          // null for pure khata/repayment entries
    val itemName: String? = null,      // denormalized (display)
    val customerId: Long? = null,      // FK→customers; null for anonymous cash sales (DB v3)
    val qtySold: Double = 0.0,
    val amount: Double,
    val unitPrice: Double? = null,     // snapshot of items.sellPrice at sale time; null if unknown/≤0 (DB v3)
    val unitCost: Double? = null,      // snapshot of items.costPrice at sale time; null if unknown/≤0 (DB v3)
    val type: String,                  // "cash" | "credit" | "repayment"
    val party: String? = null,         // denormalized customer name (display + fallback)
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

### KhataEntity (DB v3)
```kotlin
@Entity(
    tableName = "khata",
    foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["customerId"], unique = true)]   // one ledger row per customer
)
data class KhataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,              // FK→customers (DB v3)
    val partyName: String,             // denormalized for display
    val balance: Double = 0.0,         // positive = they owe us
    val lastUpdated: Long = System.currentTimeMillis()
)
```
NOTE: `KhataRepositoryImpl.adjust` resolves the party name → `customerId` via
`CustomerRepository.resolveOrCreate` (idempotent) and upserts by `customerId`. The public
`KhataRepository` API stays name-based, so `KhataScreen`/Assistant callers are unchanged.

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
System prompt (verbatim — keep in sync with LlmEngine.SALE_SYSTEM_PROMPT and any
external validation script):
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
        val stripped = raw
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
    }
}
```

Always wrap LLM parse calls in try-catch. On failure, show the user a manual entry screen — never crash.

NOTE: The model occasionally varies on `type` under sampling at temp 0.1. §18 cases are
currently 5/5 but watch edge cases — if a case regresses, tighten the prompt before
changing temperature.

---

## 7. LLM Engine Notes (llama-server HTTP)

The LLM runs as llama-server (Qwen 2.5 3B) on `127.0.0.1:8080` ON THE DEVICE.
`LlmEngine.kt` is an HTTP client (Ktor) that:
- POSTs the system prompt + user input to the local server's completion endpoint
- Uses temperature=0.1, the stop sequences above, maxTokens=256
- Runs on `Dispatchers.IO`, never the main thread
- Holds `SALE_SYSTEM_PROMPT` as the single source of truth for the prompt

**Server lifecycle for the demo:** the server must start on the phone without a tethered
Mac. Document the exact start command in `docs/demo-runbook.md`. The Phase 6 airplane-mode
test is the gate that proves this works.

If migrating to in-app inference later (time permitting): swap `LlmEngine` to call
llama.cpp JNI or LiteRT-LM directly so there is no server process at all. Use Context7
for the chosen library's Android API.

---

## 8. Voice Input Implementation (whisper.cpp)

```kotlin
// data/voice/WhisperEngine.kt — whisper.cpp via JNI
// 1. AudioRecorder captures mic input as 16kHz mono PCM (whisper requires 16kHz)
// 2. WhisperEngine.transcribe(wavPath, lang="hi") → returns transcribed text
// 3. Transcribed text flows into the SAME ParseSaleEntryUseCase as typed input
//
// Model loading: load ggml-tiny.bin (or base) once as a singleton in VoiceModule.
// Keep model in memory; do not reload per transcription.
//
// Emit a VoiceState sealed class: Idle | Recording | Transcribing | Result(text) | Error
//
// IMPORTANT: whisper.cpp needs a native build step (CMake/NDK). Use Context7 for the
// Android JNI setup. The ggml-org/whisper.cpp repo has a reference Android example.
//
// Performance: the tiny model transcribes a ~5s clip in well under 1s on the
// Snapdragon 8 Elite Gen 5. Sequence transcription AFTER recording stops, and do NOT
// run whisper and the LLM at the same time (memory-bandwidth contention).
//
// Fallback: if the native build is too costly in hackathon time, fall back to
// Android SpeechRecognizer (EXTRA_PREFER_OFFLINE=true), or ship typed-entry only.
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
// IMPORTANT: OriginOS battery optimization may kill WorkManager. Document in settings screen.
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
//
// Cache the last response in DataStore — re-use if < 6 hours old.
// This avoids API calls during the demo and handles offline gracefully.
// This is the ONLY component allowed to make a network call — and only public-equivalent
// aggregate data leaves the device. Enforce the anonymisation in code, not just policy.
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
  🎙️ Voice entry (whisper.cpp)
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

Request at runtime using Accompanist Permissions. Request RECORD_AUDIO before recording
audio for whisper. Request CAMERA before opening the scanner.

---

## 15. Implementation Phases

> STATUS (2026-06-13): Phase 0 ✅ done & device-verified · Phase 1 ✅ done & device-verified
> (§18 = 5/5). Currently entering Phase 2. SPIKE A ✅, SPIKE C ✅. SPIKE B replaced by the
> whisper.cpp spike below (gates Phase 4).

### Phase 0 — Project Foundation  ✅ DONE
**Goal:** Project builds, runs on iQOO, DB layer works, foundational spikes pass.

Tasks (all complete): libs.versions.toml, build.gradle.kts (Hilt/Room KSP/serialization),
Hilt @HiltAndroidApp, Room DB (v2) + DAOs, DatabaseModule + RepositoryModule, repository
impls, Navigation Compose (4 tabs), brand theme.

#### SPIKE A — LLM Backend Verification  ✅ PASSED
llama-server (Qwen 2.5 3B) reachable on 127.0.0.1:8080; parse returns valid output.
ACTION ITEM (carried into Phase 6): prove the server runs ON-DEVICE with airplane mode on.

#### SPIKE B — Whisper On-Device ASR Verification  ⏳ (gates Phase 4)
(Supersedes the old Android SpeechRecognizer spike. We no longer depend on OriginOS
offline system STT.)
1. Build whisper.cpp for Android (CMake + NDK) — use Context7 for setup
2. Bundle ggml-tiny.bin in assets or push via ADB
3. Record a 5s Hindi clip ("दो किलो चावल अस्सी रुपये"), transcribe it
   **Pass:** recognisable Hindi/Hinglish text returned in < 2s, offline.
   **Fail:** fall back to Android SpeechRecognizer (EXTRA_PREFER_OFFLINE=true), OR set
   VOICE_ENABLED=false and ship typed entry only. Typed entry already covers the core loop —
   do not let voice block the demo.

#### SPIKE C — OriginOS Battery Optimisation  ✅ PASSED
```bash
adb shell device_config put activity_manager max_phantom_processes 2147483647
adb shell dumpsys deviceidle whitelist +com.artha.kirana
adb shell cmd appops set com.artha.kirana RUN_IN_BACKGROUND allow
adb shell cmd appops set com.artha.kirana RUN_ANY_IN_BACKGROUND allow
adb shell dumpsys jobscheduler | grep "com.artha.kirana"   # must show jobs
```
Re-run before every demo session (resets on restart).

---

### Phase 1 — Core Sale Entry Loop  ✅ DONE & VERIFIED (§18 = 5/5)
Type a Hindi/Hinglish sale → LLM parses → editable confirmation card → Room save →
Home updates reactively. Revenue (today) aggregates correctly and excludes repayments.

---

### Phase 2 — Inventory + Khata + P&L  ⬅ CURRENT
**Goal:** Complete shop management loop — sale in, stock down, khata updated, profit shown.

Tasks:
1. Build `InventoryScreen` — item list with stock levels, low-stock highlighting
2. Build `AddItemSheet` — bottom sheet for adding items (cost/sell price/threshold)
3. Implement `InventoryAlertWorker` — WorkManager periodic check, low-stock notification
4. Build `KhataScreen` — party list with outstanding balances
5. Build `KhataPartyDetail` — transaction history for one party
6. Implement `GetPnlSummaryUseCase` with Room queries
7. Build `PnlScreen` — today/week/month tabs, gross revenue/cost/profit cards
8. Add `ProfitChart` using Vico — daily revenue bar chart (verify Kotlin-compatible version)
9. Wire all ViewModels

**Ask if needed:** "Should the low-stock notification clear automatically once the user
restocks, or persist until dismissed?"

**Checkpoint:** Log 10 varied sales → inventory counts correctly → khata balances correct
→ P&L arithmetic verified.

---

### Phase 3 — Bill Scanning
**Goal:** Snap a supplier bill → ML Kit reads it → LLM parses items → inventory updated.

Tasks:
1. Add CameraX composable to `BillScanScreen` — Context7 for latest Compose CameraX API
2. Image capture → `BillOcrEngine` (ML Kit TextRecognizer)
3. `BillParser` — OCR text → LLM with bill-parsing system prompt
4. `ScanConfirmSheet` — review parsed line items, allow editing
5. Wire `LogPurchaseUseCase` on confirm → inventory updated + purchase cost logged

**Ask if needed:** "OCR + LLM takes 3–6s. Loading skeleton or progress indicator?"

**Checkpoint:** Snap a printed receipt → items in confirmation sheet → confirm → stock up.

---

### Phase 4 — Voice Input (whisper.cpp) + Vernacular Output
**Goal:** Speak a sale in Hindi → transcribed on-device → parsed → logged. Summary spoken back.
GATED ON SPIKE B (whisper.cpp build).

Tasks:
1. Implement `AudioRecorder` (16kHz mono) + `WhisperEngine` (whisper.cpp JNI transcribe)
2. Add `VoiceEntryButton` FAB with recording animation (Compose animate APIs)
3. Wire transcribed text into the existing `ParseSaleEntryUseCase` (same pipeline as typed)
4. Implement `TextToSpeechManager` — Hindi TTS singleton
5. Add "Hear summary" button on Home → TTS reads today's P&L in Hindi
6. Add vernacular language selector in settings

**If whisper build is too costly:** fall back to Android SpeechRecognizer, or show
"Voice coming soon" and ship typed-only. Do not block the demo on voice.

**Checkpoint:** Speak "तीन साबुन बीस-बीस के" → transcribed → parsed → logged.
TTS reads "आज की बिक्री..." on tap.

---

### Phase 5 — Market Trends Insights (Claude API)
**Goal:** Claude API generates 3–5 insights from anonymised sales history.

Tasks:
1. Implement `ClaudeApiClient` with Ktor — POST to /v1/messages
2. Anonymised sales aggregation query (item name + qty only — no amounts, no parties)
3. `GetMarketInsightsUseCase` — calls Claude API, caches in DataStore for 6 hours
4. `InsightsScreen` — insight cards in Hindi/English
5. Add Insights tab to bottom nav

**Ask before implementing:** "Do you have a Claude API key ready? Store it hardcoded for
the hackathon or in local.properties?"

**Checkpoint:** Insights screen shows 3 Hinglish cards from seeded data. Works on cached
response when offline.

---

### Phase 6 — Demo Hardening
**Goal:** Zero crashes in 3 full demo runs. Airplane mode. On-device server proven. Real seeded data.

Tasks:
1. **ON-DEVICE SERVER GATE (do FIRST):** airplane mode ON, disconnect Mac/USB, confirm a
   sale parses end-to-end. If it fails, fixing the on-device llama-server is priority #1.
   Document the untethered start in `docs/demo-runbook.md`.
2. Seed realistic demo data — `DemoDataSeeder.kt` once on first launch (debug builds):
   15 sales across 5 items (rice, sugar, oil, soap, biscuits); 3 credit entries
   (Ramesh, Priya, Suresh); 2 purchases; one item near reorder threshold.
3. Add "Fix battery optimisation" setting — deep link to OriginOS power settings
4. Test all 5 §18 LLM cases — fix any regressions
5. Test bill scanning with 3 different real receipt photos
6. Test voice (whisper) offline, OR confirm graceful fallback
7. Fix keyboard/back navigation issues
8. Error states everywhere — no empty screens, no crashes on null data
9. Run 3 complete demo walkthroughs matching the script below

**DEMO SCRIPT (burn this into memory):**
1. Airplane mode ON (show judges) — and the llama-server is running ON the phone
2. Type/speak sale in Hindi → parsed → ledger
3. Credit entry for Ramesh → khata updates
4. Snap a bill → items confirmed → inventory up
5. P&L tab → "₹X gross profit today"
6. Low-stock notification fires
7. (Only now) Network ON → Insights tab → 3 Claude insights
8. Close: "Everything except the last screen ran on the iQOO. Nothing left this phone."

---

## 16. Coding Standards

- **Kotlin idioms only** — use `?.` and `?:`, no Java-style null handling
- **No hardcoded strings** — UI text in `strings.xml` with Hindi variants in `values-hi/strings.xml`
- **StateFlow everywhere** — no LiveData, no callbacks in UI layer
- **`collectAsStateWithLifecycle`** — not `collectAsState`
- **Coroutines only** — no threads, AsyncTask, or RxJava
- **Hilt for everything** — no manual DI, no singletons outside Hilt modules
- **Sealed classes for UI state:**
  ```kotlin
  sealed class UiState<out T> {
      object Loading : UiState<Nothing>()
      data class Success<T>(val data: T) : UiState<T>()
      data class Error(val message: String) : UiState<Nothing>()
  }
  ```
- **Never call the LLM or whisper on the main thread** — always `withContext(Dispatchers.IO)`
- **Never run whisper and the LLM simultaneously** — sequence them (memory bandwidth)
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
- IndicBERT or any encoder/classifier model — evaluated and rejected: it cannot generate
  the structured JSON the parser needs. Qwen 2.5 3B is the parser. (Note for judges only.)

If asked about any of these: "That's on the roadmap after the hackathon — out of scope for the 30-hour build."

---

## 18. Quick Reference — LLM Test Cases

Always validate these before demo (currently 5/5, stable across 3 runs):
| Input | Expected type | Expected party |
|---|---|---|
| "दो किलो चावल, अस्सी रुपये, उधार रमेश को" | credit | Ramesh |
| "2 kilo cheeni forty rupees" | cash | null |
| "रमेश ने पचास रुपये दिए" | repayment | Ramesh |
| "teen soap bees-bees ke credit Priya" | credit | Priya |
| "chawal aur daal kul 120" | cash | null |

If a case regresses: tighten the system prompt first (keep `LlmEngine.SALE_SYSTEM_PROMPT`
and any validation script in sync). Only then touch temperature/stop-tokens.

---

*Artha Kirana · CLAUDE.md · iQOO Hackathon 2026 · v1.1 (Whisper + on-device-server requirement)*
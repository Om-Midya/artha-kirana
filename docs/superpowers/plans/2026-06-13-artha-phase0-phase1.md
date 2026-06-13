# Artha Kirana — Phase 0 + Phase 1 Implementation Plan

> **Status (historical):** Phase 0 + Phase 1 are DONE & device-verified. This plan is kept for reference. Current state: `docs/STATUS.md`; canonical spec: `CLAUDE.md`. Note `CLAUDE.md` has since changed voice to **whisper.cpp** (SPIKE B redefined) and added a **hard on-device-server requirement** — follow `CLAUDE.md` for Phases 2–6, not the older SPIKE/voice wording below.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Artha Kirana foundation (Gradle, Hilt, Room, Navigation, theme) and the first demoable slice — type a Hindi sale → on-device LLM (llama-server over localhost HTTP) parses it → Room DB → Home screen updates.

**Architecture:** Clean Architecture (data/domain/ui) + MVVM + StateFlow + single-Activity Navigation Compose. The on-device LLM is **not** in-process — `LlmEngine` is a **Ktor HTTP client** that POSTs to `http://127.0.0.1:8080/v1/chat/completions`, where `llama-server` serves Qwen 2.5 3B on the phone. The cloud Claude API (later phases) reuses the same Ktor pattern. See `docs/superpowers/specs/2026-06-13-artha-llm-http-architecture-design.md` and `CLAUDE.md`.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.09), Material 3, Hilt, Room (KSP, no SQLCipher yet), Navigation Compose, Ktor (OkHttp engine) + kotlinx.serialization, Accompanist Permissions, Timber.

> **Toolchain deviation (applied during execution):** the scaffold shipped **AGP 9.0.1 / Gradle 9.2.1**, which is incompatible with Hilt + KSP + Compose (Hilt only added AGP-9 support in 2.59+, shipped broken; Compose wants AGP ≥ 9.2). We downgraded to **AGP 8.13.0 / Gradle 8.13** — the last stable 8.x and the combo the spec's libraries were written against (`compileSdk 36` needs AGP ≥ 8.9). Also: removed the AGP-9 `gradle-daemon-jvm.properties` (JDK-21 pin) to run the daemon on the launching JDK 17; changed the `compileSdk` DSL from the AGP-9 `release(36){}` block to `compileSdk = 36`; restored `distributionSha256Sum` for the 8.13 wrapper (verified official checksum). **Vico (charts) is deferred to Phase 2** — 3.1.0 requires Kotlin 2.3.x; pick a Kotlin-2.0-compatible version there.

**Source-of-truth docs:**
- `CLAUDE.md` — canonical spec (DB schema §4, prompts §5, JSON safety §6, P&L §10, coding standards §16, test cases §18).
- Design doc (above) — the LLM-over-HTTP delta. Where it disagrees with `CLAUDE.md`, the design doc wins.

**Per the spec §0:** before writing code against any library, confirm its current API via Context7. Ktor (OkHttp engine + `ContentNegotiation`/`json()`) and Vico (3.1.0, `CartesianChartHost`) were verified during planning. Verify Room/KSP, Hilt, Navigation, CameraX, ML Kit at the phase they first appear.

---

## File Structure (Phase 0 + Phase 1)

| File | Responsibility |
|---|---|
| `gradle/libs.versions.toml` | Version catalog — all deps |
| `build.gradle.kts` (root) | Hilt, KSP, serialization plugins (apply false) |
| `app/build.gradle.kts` | Apply plugins, dependency list |
| `app/src/main/AndroidManifest.xml` | Permissions, `network_security_config`, `.ArthaApplication` |
| `app/src/main/res/xml/network_security_config.xml` | Cleartext to `127.0.0.1` only |
| `scripts/start-llama-server.sh` | ADB helper — launch llama-server (demo step 0) |
| `ArthaApplication.kt` | `@HiltAndroidApp` + Timber init |
| `ui/MainActivity.kt` | `@AndroidEntryPoint`, sets `ArthaApp` |
| `ui/ArthaApp.kt` | `NavHost` + bottom nav (4 tabs) |
| `ui/theme/{Color,Type,Theme}.kt` | Brand theme (§13) |
| `data/db/ArthaDatabase.kt` + `entity/*` + `dao/*` | Room: 5 entities, 5 DAOs |
| `data/repository/*Impl.kt` | Repository implementations |
| `domain/repository/*.kt` | Repository interfaces |
| `domain/model/*.kt` | Domain models incl. `SaleEntry` |
| `data/remote/LlmHttpClient.kt` + `dto/*` | Ktor client → llama-server |
| `data/llm/{LlmEngine,SaleParser}.kt` | Prompt orchestration + JSON extraction |
| `util/JsonParser.kt` | Safe JSON extraction (§6) |
| `domain/usecase/{ParseSaleEntryUseCase,LogSaleUseCase}.kt` | Phase 1 use-cases |
| `ui/home/{HomeScreen,HomeViewModel}.kt` | Today's summary + recent entries |
| `ui/entry/{SaleEntryScreen,SaleEntryViewModel}.kt` | Typed entry + confirmation card |
| `di/{DatabaseModule,RepositoryModule,NetworkModule}.kt` | Hilt modules |

---

# PHASE 0 — Foundation

**Phase goal:** App builds and runs on the iQOO. DB initialises. Navigation works (4 tabs). LLM connectivity verified against llama-server. STT + battery spikes run. Ends in a buildable, navigable shell.

---

### Task 0.1: Version catalog — add all dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Replace the `[versions]`, `[libraries]`, `[plugins]` sections**

```toml
[versions]
agp = "9.0.1"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"            # MUST match kotlin version; verify via Context7/KSP releases
coreKtx = "1.13.1"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.09.00"
navigationCompose = "2.8.4"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
room = "2.6.1"
ktor = "2.3.12"                  # verify latest 2.x via Context7
kotlinxSerialization = "1.7.3"
accompanistPermissions = "0.36.0"
datastore = "1.1.1"
coil = "2.7.0"
timber = "5.0.1"
vico = "3.1.0"
workManager = "2.9.1"
mlkitTextRecognition = "16.0.1"
camerax = "1.4.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
mockk = "1.13.13"
coroutinesTest = "1.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktor" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanistPermissions" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
timber = { group = "com.jakewharton.timber", name = "timber", version.ref = "timber" }
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
androidx-camera-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 2: Sync** — Run `./gradlew help` (or Android Studio Gradle sync). Expected: catalog resolves, no "unknown version ref" errors. If a version 404s, confirm the latest via Context7 and adjust.

- [ ] **Step 3: Commit** — `git add gradle/libs.versions.toml && git commit -m "build: add full dependency catalog"`

---

### Task 0.2: Root + app Gradle — apply plugins & dependencies

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 2: `app/build.gradle.kts` — plugins block**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
```

- [ ] **Step 3: `app/build.gradle.kts` — android block** (keep existing `namespace`, `compileSdk`, `defaultConfig`; ensure `testInstrumentationRunner` and add `buildConfigField` for the LLM base URL)

```kotlin
android {
    namespace = "com.artha.kirana"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.artha.kirana"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "LLM_BASE_URL", "\"http://127.0.0.1:8080\"")
        buildConfigField("boolean", "VOICE_ENABLED", "true")
    }
    buildTypes {
        release { isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { compose = true; buildConfig = true }
}
```

- [ ] **Step 4: `app/build.gradle.kts` — dependencies**

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.timber)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 5: Build** — Run `./gradlew :app:assembleDebug`. Expected: BUILD SUCCESSFUL (the default template still compiles; we've only added deps). If Hilt/KSP version mismatch errors appear, reconcile `ksp` version to the Kotlin version.

- [ ] **Step 6: Commit** — `git add build.gradle.kts app/build.gradle.kts && git commit -m "build: apply Hilt/KSP/serialization plugins and dependencies"`

---

### Task 0.3: Manifest, permissions, network security config, Application class

**Files:**
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/java/com/artha/kirana/ArthaApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: `network_security_config.xml`** — allow cleartext only to loopback

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
</network-security-config>
```

- [ ] **Step 2: `ArthaApplication.kt`**

```kotlin
package com.artha.kirana

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ArthaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
```

- [ ] **Step 3: `AndroidManifest.xml`** — add permissions + wire Application + network config (inside `<manifest>`, before `<application>`, add perms; set `android:name`, `android:networkSecurityConfig`)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```

In `<application ...>` add attributes:
```xml
android:name=".ArthaApplication"
android:networkSecurityConfig="@xml/network_security_config"
```

- [ ] **Step 4: Build** — `./gradlew :app:assembleDebug`. Expected: BUILD SUCCESSFUL, Hilt generates `Hilt_ArthaApplication`.

- [ ] **Step 5: Commit** — `git add app/src/main && git commit -m "feat: Hilt Application, permissions, loopback cleartext config"`

---

### Task 0.4: Brand theme (Color, Type, Theme)

**Files:**
- Modify: `app/src/main/java/com/artha/kirana/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/artha/kirana/ui/theme/Theme.kt`
- (Type.kt: keep default unless adjusting; optional Devanagari-friendly font later)

**Design note (frontend-design principles):** This is a vernacular, trust-first finance app for shopkeepers. Favor high-contrast, large-touch-target, currency-forward UI. Brand gold (`#E6AC00`) as primary accent on a dark surface reads as "premium ledger." Use semantic color (green=profit/cash, red=outstanding/credit) consistently across Home, Khata, P&L.

- [ ] **Step 1: `Color.kt`** (§13 brand colors)

```kotlin
package com.artha.kirana.ui.theme
import androidx.compose.ui.graphics.Color

val BrandGold   = Color(0xFFE6AC00)
val BrandDark   = Color(0xFF1A1A1A)
val AccentGreen = Color(0xFF2D7D46)
val AccentRed   = Color(0xFFC0392B)
val AccentBlue  = Color(0xFF1A5276)
val SurfaceDark = Color(0xFF1E1E1E)
```

- [ ] **Step 2: `Theme.kt`** — define a dark-first Material3 scheme using brand colors and apply it (replace the dynamic-color template body)

```kotlin
package com.artha.kirana.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ArthaDarkColors = darkColorScheme(
    primary = BrandGold, onPrimary = BrandDark,
    secondary = AccentBlue, tertiary = AccentGreen,
    error = AccentRed, background = BrandDark, surface = SurfaceDark,
)
private val ArthaLightColors = lightColorScheme(
    primary = BrandGold, secondary = AccentBlue, tertiary = AccentGreen, error = AccentRed,
)

@Composable
fun ArthaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) ArthaDarkColors else ArthaLightColors,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **Step 3: Build** — `./gradlew :app:assembleDebug`. Expected: SUCCESSFUL. (Rename references if the template used `ArthaTheme` already; align names.)

- [ ] **Step 4: Commit** — `git add app/src/main/java/com/artha/kirana/ui/theme && git commit -m "feat: Artha brand theme"`

---

### Task 0.5: Room entities (5)

**Files:**
- Create: `data/db/entity/{ItemEntity,SaleEntity,PurchaseEntity,KhataEntity,KhataTransactionEntity}.kt`

- [ ] **Step 1: Create the 5 entities** exactly as `CLAUDE.md` §4 (package `com.artha.kirana.data.db.entity`). Reproduce all fields verbatim — `ItemEntity`, `SaleEntity`, `PurchaseEntity`, `KhataEntity`, `KhataTransactionEntity` (with the two `@ForeignKey`s on `KhataTransactionEntity`). Do not alter column names; P&L queries depend on them.

- [ ] **Step 2: Build** — `./gradlew :app:assembleDebug`. Expected: SUCCESSFUL (entities compile standalone).

- [ ] **Step 3: Commit** — `git add app/src/main/java/com/artha/kirana/data/db/entity && git commit -m "feat: Room entities"`

---

### Task 0.6: DAOs (5) + Database class

**Files:**
- Create: `data/db/dao/{ItemsDao,SalesDao,PurchasesDao,KhataDao,KhataTransactionDao}.kt`
- Create: `data/db/ArthaDatabase.kt`

- [ ] **Step 1: DAOs** — each DAO exposes `Flow`-returning reads (coding standard §16) and `suspend` writes. Minimum for Phase 1: `SalesDao.insert(sale): Long`, `SalesDao.observeToday(start: Long): Flow<List<SaleEntity>>`, `SalesDao.sumAmountBetween(...)`; `ItemsDao.observeAll(): Flow<List<ItemEntity>>`, `ItemsDao.findByName(name): ItemEntity?`, `ItemsDao.decrementStock(id, qty)`; `KhataDao.upsertBalance(...)`, `KhataDao.observeAll(): Flow<List<KhataEntity>>`. Add `@Insert`, `@Query`, `@Update` annotations.

- [ ] **Step 2: `ArthaDatabase.kt`** — `@Database(entities=[...5...], version=1)` abstract class with the 5 dao accessors. **No SQLCipher** (design §3.1).

```kotlin
@Database(
    entities = [ItemEntity::class, SaleEntity::class, PurchaseEntity::class,
                KhataEntity::class, KhataTransactionEntity::class],
    version = 1, exportSchema = false,
)
abstract class ArthaDatabase : RoomDatabase() {
    abstract fun itemsDao(): ItemsDao
    abstract fun salesDao(): SalesDao
    abstract fun purchasesDao(): PurchasesDao
    abstract fun khataDao(): KhataDao
    abstract fun khataTransactionDao(): KhataTransactionDao
}
```

- [ ] **Step 3: Build** — `./gradlew :app:assembleDebug`. Expected: Room KSP generates `ArthaDatabase_Impl`. Fix any `@Query` SQL the compiler rejects.

- [ ] **Step 4: Commit** — `git add app/src/main/java/com/artha/kirana/data/db && git commit -m "feat: Room DAOs and database"`

---

### Task 0.7: DatabaseModule (Hilt) — with SQLCipher swap-in seam

**Files:**
- Create: `di/DatabaseModule.kt`

- [ ] **Step 1: Provide DB + DAOs.** Comment the exact line where an encrypted `SupportFactory` would be installed later (design §3.1).

```kotlin
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ArthaDatabase =
        Room.databaseBuilder(ctx, ArthaDatabase::class.java, "artha.db")
            // SQLCipher swap-in seam: .openHelperFactory(SupportFactory(passphrase)) — deferred (design §3.1)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun itemsDao(db: ArthaDatabase) = db.itemsDao()
    @Provides fun salesDao(db: ArthaDatabase) = db.salesDao()
    @Provides fun purchasesDao(db: ArthaDatabase) = db.purchasesDao()
    @Provides fun khataDao(db: ArthaDatabase) = db.khataDao()
    @Provides fun khataTransactionDao(db: ArthaDatabase) = db.khataTransactionDao()
}
```

- [ ] **Step 2: Build** — `./gradlew :app:assembleDebug`. Expected: SUCCESSFUL.
- [ ] **Step 3: Commit** — `git add app/src/main/java/com/artha/kirana/di && git commit -m "feat: Hilt DatabaseModule"`

---

### Task 0.8: Navigation shell — ArthaApp (4 tabs) + MainActivity

**Files:**
- Modify: `ui/MainActivity.kt`
- Create: `ui/ArthaApp.kt`
- Create: placeholder screens `ui/home/HomeScreen.kt`, `ui/inventory/InventoryScreen.kt`, `ui/khata/KhataScreen.kt`, `ui/pnl/PnlScreen.kt` (simple `Text` stubs for now)

- [ ] **Step 1: Define routes + bottom nav.** `ArthaApp.kt` with a sealed `Destination(route, labelRes, icon)` list of 4 tabs (Home, Inventory, Khata, P&L), `Scaffold` + `NavigationBar` + `NavHost`. Use `material-icons-extended` (Home, Inventory2, MenuBook, BarChart).

- [ ] **Step 2: `MainActivity.kt`** — `@AndroidEntryPoint`, `setContent { ArthaTheme { ArthaApp() } }`.

- [ ] **Step 3: Stub screens** — each a centered `Text("Home")` etc.

- [ ] **Step 4: Build + install** — `./gradlew :app:installDebug` with the iQOO connected. Expected: app launches, 4 tabs switch. **Manual checkpoint.**

- [ ] **Step 5: Commit** — `git add app/src/main/java/com/artha/kirana/ui && git commit -m "feat: navigation shell with 4 tabs"`

---

### Task 0.9: SPIKE A — LLM connectivity (replaces in-process LLM spike)

**Files:**
- Create: `scripts/start-llama-server.sh`
- Temporary: a debug button on `HomeScreen` or a `Log` in `MainActivity` that calls a throwaway `LlmHttpClient.health()` + `completion("Say: OK")`

- [ ] **Step 1: `scripts/start-llama-server.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail
ADB="${ADB:-adb}"
"$ADB" shell "cd /data/local/tmp/llama/llama-b9620 && LD_LIBRARY_PATH=. nohup ./llama-server \
  -m /sdcard/Download/qwen2.5-3b-instruct-q5_k_m.gguf --host 0.0.0.0 --port 8080 \
  > /sdcard/Download/llama-server.log 2>&1 &"
echo "llama-server starting on the phone (port 8080). Tail: adb shell tail -f /sdcard/Download/llama-server.log"
```

- [ ] **Step 2: Start the server** — `chmod +x scripts/start-llama-server.sh && ./scripts/start-llama-server.sh`. Wait ~10s (cold mmap of 2.4GB). Verify: `adb shell tail -n 5 /sdcard/Download/llama-server.log` shows `HTTP server listening`.

- [ ] **Step 3: Temporary in-app check** — from `MainActivity.onCreate`, `lifecycleScope.launch(Dispatchers.IO)` a raw `HttpClient(OkHttp).get("http://127.0.0.1:8080/health")` and a small `POST /completion {"prompt":"Say: OK","n_predict":8}`. `Timber.d` the status + body + elapsed ms. (This is throwaway; remove after Task 1.x lands the real `LlmHttpClient`.)

- [ ] **Step 4: Run on device** — launch app. **Pass:** Logcat (`adb logcat -s Timber`) shows HTTP 200 + a token, round-trip <5s. **Fail:** connection refused → server not running (re-run step 2); empty reply → server still cold-loading (wait, retry).

- [ ] **Step 5: Record result** — add a comment in the design doc / `LlmEngine.kt` later with the measured ms. Remove the throwaway code. Commit the script: `git add scripts/start-llama-server.sh && git commit -m "chore: llama-server launch script + SPIKE A connectivity verified"`

---

### Task 0.10: SPIKE B — Hindi offline STT (unchanged from spec)

- [ ] **Step 1:** Add the temporary `verifyHindiStt()` + `sttLauncher` from `CLAUDE.md` §15 SPIKE B to `MainActivity`.
- [ ] **Step 2:** Turn airplane mode ON. Trigger it. Speak "दो किलो चावल".
- [ ] **Step 3: Pass** = Toast/Logcat shows Hindi text → keep `VOICE_ENABLED=true`. **Fail** = error/empty → set `buildConfigField VOICE_ENABLED=false` in Task 0.2; Phase 4 shows "Voice coming soon". Do NOT debug STT during the hackathon.
- [ ] **Step 4:** Remove the temporary code. Commit: `git commit -am "chore: SPIKE B Hindi offline STT result recorded"`

---

### Task 0.11: SPIKE C — OriginOS battery (ADB, from Mac)

- [ ] **Step 1:** Run the 4 ADB commands from `CLAUDE.md` §15 SPIKE C (`device_config`, `deviceidle whitelist`, two `appops`).
- [ ] **Step 2: Verify** — `adb shell dumpsys deviceidle whitelist | grep com.artha.kirana` shows the package.
- [ ] **Step 3:** Document the manual fallback (Settings → Apps → Artha → Battery Unrestricted / Auto-start ON) in a `docs/demo-runbook.md`. Commit.

**Phase 0 Checkpoint:** App launches on iQOO, 4 tabs navigate, DB initialises (no crash on first launch), SPIKE A returns a token from llama-server, SPIKE B + C results recorded. Buildable shell complete.

---

# PHASE 1 — Core Sale Entry Loop

**Phase goal:** Type a Hindi sale → `LlmEngine` (HTTP) parses → confirmation card → DB write → Home updates. Validate §18 test cases (>90% parse).

---

### Task 1.1: JsonParser (TDD — pure JVM unit test)

**Files:**
- Create: `util/JsonParser.kt`
- Test: `app/src/test/java/com/artha/kirana/util/JsonParserTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package com.artha.kirana.util
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonParserTest {
    @Test fun extractsPlainJson() =
        assertEquals("""{"a":1}""", JsonParser.extractJson("""{"a":1}"""))
    @Test fun stripsMarkdownFences() =
        assertEquals("""{"a":1}""", JsonParser.extractJson("```json\n{\"a\":1}\n```"))
    @Test fun stripsPreamble() =
        assertEquals("""{"a":1}""", JsonParser.extractJson("Here is the JSON: {\"a\":1} thanks"))
    @Test fun returnsNullWhenNoBraces() = assertNull(JsonParser.extractJson("no json here"))
}
```

- [ ] **Step 2: Run, verify fail** — `./gradlew :app:testDebugUnitTest --tests "*JsonParserTest"`. Expected: FAIL (unresolved `JsonParser`).

- [ ] **Step 3: Implement** (`CLAUDE.md` §6, hardened to find outermost braces)

```kotlin
package com.artha.kirana.util
object JsonParser {
    fun extractJson(raw: String): String? {
        val stripped = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = stripped.indexOf('{'); val end = stripped.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) return null
        return stripped.substring(start, end + 1)
    }
}
```

- [ ] **Step 4: Run, verify pass** — same command. Expected: PASS (4 tests).
- [ ] **Step 5: Commit** — `git commit -am "feat: safe JSON extraction with tests"`

---

### Task 1.2: LLM DTOs + LlmHttpClient

**Files:**
- Create: `data/remote/dto/{ChatCompletionRequest,ChatCompletionResponse}.kt`
- Create: `data/remote/LlmHttpClient.kt`
- Create: `di/NetworkModule.kt`
- Test: `app/src/test/java/com/artha/kirana/data/remote/LlmDtoTest.kt`

- [ ] **Step 1: DTOs** (`@Serializable`, OpenAI-compatible shape verified against handoff)

```kotlin
package com.artha.kirana.data.remote.dto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ChatMessage(val role: String, val content: String)
@Serializable data class ChatCompletionRequest(
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
    @SerialName("max_tokens") val maxTokens: Int = 256,
    val stop: List<String> = listOf("```"),
)
@Serializable data class ChatChoice(val message: ChatMessage)
@Serializable data class ChatCompletionResponse(val choices: List<ChatChoice>)
```

- [ ] **Step 2: DTO round-trip test** — serialize a request, deserialize a sample llama-server response JSON, assert `choices[0].message.content`. Run `./gradlew :app:testDebugUnitTest --tests "*LlmDtoTest"` → FAIL then PASS.

- [ ] **Step 3: `NetworkModule.kt`** — provide a single configured `HttpClient(OkHttp)` (ContentNegotiation + json + Logging + 60s request timeout for cold loads), verified Ktor pattern.

```kotlin
@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 60_000 }
        if (BuildConfig.DEBUG) install(Logging) { level = LogLevel.INFO }
    }
}
```

- [ ] **Step 4: `LlmHttpClient.kt`** — `health(): Boolean` (`GET /health`) and `chat(system, user): String` (`POST /v1/chat/completions`, returns `choices[0].message.content`). Base URL = `BuildConfig.LLM_BASE_URL`. Wrap in try/catch → throw a typed `LlmUnavailable` on failure.

- [ ] **Step 5: Build** — `./gradlew :app:assembleDebug`. Expected: SUCCESSFUL.
- [ ] **Step 6: Commit** — `git commit -am "feat: Ktor LLM client + DTOs + NetworkModule"`

---

### Task 1.3: SaleEntry model, SaleParser, LlmEngine

**Files:**
- Create: `domain/model/SaleEntry.kt`
- Create: `data/llm/SaleParser.kt` (system prompt §5, parses content → `List<SaleEntry>` via JsonParser + kotlinx)
- Create: `data/llm/LlmEngine.kt` (injects `LlmHttpClient`; `parseSale(text): Result<List<SaleEntry>>`)
- Test: `app/src/test/java/com/artha/kirana/data/llm/SaleParserTest.kt`

- [ ] **Step 1: `SaleEntry`** — `data class SaleEntry(val item: String?, val qty: String?, val amount: Double?, val type: String, val party: String?)` + a `@Serializable SaleEntryDto` + wrapper `{"entries":[...]}`.

- [ ] **Step 2: SaleParser test (TDD)** — feed a raw model string `{"entries":[{"item":"चावल","qty":"2 kg","amount":80,"type":"credit","party":"रमेश"}]}` → assert one `SaleEntry` with `type=="credit"`, `party=="रमेश"`. Also test markdown-fenced input routes through `JsonParser`. Run → FAIL then PASS.

- [ ] **Step 3: Implement `SaleParser`** — `JsonParser.extractJson` → `Json.decodeFromString` → map DTO→domain; on any exception return empty list (caller shows manual form).

- [ ] **Step 4: Implement `LlmEngine.parseSale`** — build `ChatMessage(system=§5 verbatim, user=text)`, call `client.chat`, pass to `SaleParser`. Return `Result.failure(LlmUnavailable)` if client throws.

- [ ] **Step 5: Run unit tests** → PASS. Commit `git commit -am "feat: SaleParser + LlmEngine with tests"`.

---

### Task 1.4: Domain repositories + use-cases (LogSaleUseCase)

**Files:**
- Create: `domain/repository/{SalesRepository,InventoryRepository,KhataRepository}.kt`
- Create: `data/repository/{SalesRepositoryImpl,InventoryRepositoryImpl,KhataRepositoryImpl}.kt`
- Create: `di/RepositoryModule.kt`
- Create: `domain/usecase/{ParseSaleEntryUseCase,LogSaleUseCase}.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/LogSaleUseCaseTest.kt`

- [ ] **Step 1: Interfaces** — `SalesRepository.logSale(sale: SaleEntity): Long`, `observeToday(): Flow<List<SaleEntity>>`, `sumRevenueToday(): Flow<Double>`; `InventoryRepository.findByName`, `decrementStock`; `KhataRepository.applyCredit(party, amount)`, `applyRepayment(party, amount)`.

- [ ] **Step 2: Impls** — delegate to DAOs from Task 0.6. `@Inject constructor(dao...)`.

- [ ] **Step 3: `RepositoryModule`** — `@Binds` each interface to its impl.

- [ ] **Step 4: `LogSaleUseCase` test (TDD with mockk)** — given a `SaleEntry(type="credit", party="Ramesh", amount=80, item="rice", qty="2")`, verify: a `SaleEntity` is inserted, item stock decremented, and `khata.applyCredit("Ramesh", 80)` called. Mock the three repos. Run → FAIL then PASS.

- [ ] **Step 5: Implement `LogSaleUseCase`** — map `SaleEntry`→`SaleEntity`; insert; if `item!=null` resolve item & decrement stock; if `type=="credit"` applyCredit, if `"repayment"` applyRepayment. `ParseSaleEntryUseCase` just delegates to `LlmEngine.parseSale`.

- [ ] **Step 6: Run tests → PASS. Commit** `git commit -am "feat: repositories + LogSaleUseCase with tests"`.

---

### Task 1.5: SaleEntryViewModel + SaleEntryScreen (typed entry + confirmation)

**Files:**
- Create: `ui/entry/SaleEntryViewModel.kt`
- Create: `ui/entry/SaleEntryScreen.kt`
- Modify: `ui/ArthaApp.kt` (add `sale_entry` route, FAB on Home → navigates here)

**Design note (frontend-design):** The confirmation card is the trust moment — show parsed fields as large, editable chips (item · qty · ₹amount · type · party) with an unmistakable green Confirm and a secondary Cancel. On parse failure show the raw text + a manual form (never a dead end). Loading state while the LLM runs: an inline indeterminate indicator with "समझ रहा हूँ…" (understanding…), not a blocking spinner.

- [ ] **Step 1: ViewModel** — `UiState` sealed (`Idle`, `Parsing`, `Confirm(entries)`, `ManualFallback(raw)`, `Error(msg)`). `parse(text)` → `Parsing` → `ParseSaleEntryUseCase`; success→`Confirm`, empty/failure→`ManualFallback`. `confirm(entry)` → `LogSaleUseCase` → `Idle` + emit a "saved" event. Use `viewModelScope`, `Dispatchers.IO` for the call.

- [ ] **Step 2: Screen** — `@Composable SaleEntryScreen(vm: SaleEntryViewModel = hiltViewModel())`. `OutlinedTextField` + "Parse" button; render the confirmation card / manual form per state; `collectAsStateWithLifecycle`.

- [ ] **Step 3: Wire route** in `ArthaApp` and the Home FAB.

- [ ] **Step 4: Build + install** — `./gradlew :app:installDebug`. With llama-server running, type `दो किलो चावल अस्सी रुपये उधार रमेश` → confirmation card shows item=चावल/rice, type=credit, party=रमेश. **Manual checkpoint.**

- [ ] **Step 5: Commit** `git commit -am "feat: sale entry screen with LLM parse + confirmation"`.

---

### Task 1.6: HomeViewModel + HomeScreen (today's summary + recents)

**Files:**
- Create: `ui/home/HomeViewModel.kt`
- Modify: `ui/home/HomeScreen.kt`

- [ ] **Step 1: HomeViewModel** — expose `todayRevenue: StateFlow<Double>` (from `SalesRepository.sumRevenueToday()`) and `recentSales: StateFlow<List<SaleEntity>>` (`observeToday()`), via `stateIn(viewModelScope, WhileSubscribed(5000), initial)`.

- [ ] **Step 2: HomeScreen** — a hero "आज की बिक्री ₹X" card (gold accent), a recent-entries `LazyColumn`, and the central FAB (→ `sale_entry`). `collectAsStateWithLifecycle`.

- [ ] **Step 3: Build + install.** Log a sale via Task 1.5 → return to Home → today's total + the new row appear (Flow-driven, no manual refresh). **Manual checkpoint — Phase 1 done.**

- [ ] **Step 4: Commit** `git commit -am "feat: home screen with today summary and recents"`.

---

### Task 1.7: Validate §18 LLM test cases

- [ ] **Step 1:** With llama-server running, enter each of the 5 §18 inputs in `SaleEntryScreen`. Record parsed `type`/`party` vs expected.
- [ ] **Step 2: Pass** = ≥90% (≥5/5 ideally) match. **Fail** = adjust temperature/stop tokens in `ChatCompletionRequest`, or refine the §5 system prompt; if still failing check llama-server sampling params. Document results in `docs/demo-runbook.md`.
- [ ] **Step 3: Commit** any prompt/param tweaks `git commit -am "fix: tune sale parser prompt for §18 test cases"`.

**Phase 1 Checkpoint:** Type a Hindi sale → parsed → confirmed → DB → Home updates live. §18 cases ≥90%.

---

# PHASES 2–6 — Roadmap (expand each into its own plan when reached)

Each phase below gets its own bite-sized plan generated at phase start (re-invoke writing-plans), following `CLAUDE.md` §15 task lists with these architecture-specific notes:

- **Phase 2 — Inventory + Khata + P&L.** Build `InventoryScreen` + `AddItemSheet` + low-stock highlighting; `InventoryAlertWorker` (WorkManager periodic 30min → notification, gated by SPIKE C); `KhataScreen` + `KhataPartyDetail`; `GetPnlSummaryUseCase` (Room aggregate queries §10, computed not stored); `PnlScreen` with **Vico 3.1.0** `CartesianChartHost` + `rememberColumnCartesianLayer` + `CartesianChartModelProducer.runTransaction { columnSeries { series(...) } }` (verified API) for a weekly revenue bar chart. Context7: Vico done, verify WorkManager.

- **Phase 3 — Bill Scanning.** CameraX Compose preview + capture → `BillOcrEngine` (ML Kit `TextRecognition`) → `BillParser` (reuses `LlmHttpClient` with the §5 bill system prompt) → `ScanConfirmSheet` (editable) → `LogPurchaseUseCase`. Context7: verify CameraX Compose + ML Kit at phase start. Show a 3–6s loading skeleton (per spec note).

- **Phase 4 — Voice + Vernacular.** `SpeechRecognizerManager` (offline Hindi STT, sealed state) — **only if SPIKE B passed**; feed recognized text into the *same* `ParseSaleEntryUseCase` pipeline. `TextToSpeechManager` (Hindi TTS). "Hear summary" on Home. Vernacular selector in settings (DataStore).

- **Phase 5 — Market Trends.** `ClaudeApiClient` (Ktor → `api.anthropic.com/v1/messages`, model `claude-sonnet-4-6`, `anthropic-version: 2023-06-01`) — **reuses the NetworkModule `HttpClient`**, different base URL + headers. Anonymised aggregates only (item+qty, no parties/amounts/timestamps). Cache 6h in DataStore. `InsightsScreen` + Insights tab. **Ask the user for the API key** (local.properties vs BuildConfig) before implementing — per spec §15 Phase 5.

- **Phase 6 — Demo Hardening.** `DemoDataSeeder` (debug-only first launch: 15 sales/5 items/3 credit parties/2 purchases/1 near-threshold item). "Fix battery" deep link. Run all §18 cases, 3 receipts, voice offline. Error states everywhere. 3 full demo walkthroughs per the §15 script. **Demo runbook step 0 = `./scripts/start-llama-server.sh`.**

---

## Self-Review Notes

- **Spec coverage:** Phase 0 covers spec §15 Phase 0 (foundation + 3 spikes, SPIKE A rewritten per design §2.3). Phase 1 covers §15 Phase 1 (LlmEngine→parse→DB→Home) + §6 JsonParser + §18 validation. §4 entities, §5 prompts, §16 standards applied. Phases 2–6 mapped to roadmap with architecture notes. No spec section dropped except SQLCipher (explicitly deferred, design §3.1) and in-process LLM (replaced by HTTP, design §2).
- **Type consistency:** `LlmHttpClient.chat(system,user)` / `health()` used consistently in Tasks 1.2–1.3; `LogSaleUseCase` repo method names (`logSale`, `decrementStock`, `applyCredit`/`applyRepayment`) consistent across Tasks 0.6/1.4. `SaleEntry` fields match §5 schema.
- **TDD adaptation:** unit-tested where cheap (JsonParser, DTOs, SaleParser, LogSaleUseCase); build-verify + manual checkpoint for Compose/Hilt/Room wiring — honors `CLAUDE.md` phase-gate model (user spec = highest priority).
- **Open item for user:** Phase 5 needs the Claude API key decision (deferred to that phase per spec).

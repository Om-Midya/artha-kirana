# Cloud LLM + Local Fallback + Cloud OCR — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a cloud LLM (OpenRouter → Claude Haiku 4.5) the primary parser for all text, with the on-device llama-server as automatic fallback, plus a cloud-vision bill-scan that restocks inventory.

**Architecture:** Introduce a `ChatClient` interface at the single LLM chokepoint (`chat(system,user,responseFormat)` + `health()`). Today's `LlmHttpClient` becomes the LOCAL impl; a new `CloudChatClient` (Ktor/OpenRouter) and `FallbackChatClient(cloud,local)` are added; Hilt binds `ChatClient → FallbackChatClient`; `IntentRouter`/`LlmEngine` inject the interface. A new cloud-vision client + `LogPurchaseUseCase` power a system-camera bill-scan screen. Light UI polish (`asRupees()` + an engine badge) surfaces which backend answered.

**Tech Stack:** Kotlin, Hilt, Ktor (OkHttp) + kotlinx.serialization, Jetpack Compose/Material3, Room v3, JUnit + mockk + kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-06-14-cloud-llm-ocr-design.md`

**Branch:** `feat/analytics-chat` (stay).

**Test command:** `./gradlew :app:testDebugUnitTest`
**Build command:** `./gradlew :app:assembleDebug`
**Install:** `./gradlew :app:installDebug` · device serial `10BFBG0CEL001DB`

---

## File Structure

**Part A — Cloud chat seam**
- Modify: `app/build.gradle.kts` — load `keys.properties` → BuildConfig (`OPENROUTER_KEY`/`OPENROUTER_MODEL`/`OPENROUTER_VISION_MODEL`/`FORCE_LOCAL_LLM`)
- Create: `app/src/main/java/com/artha/kirana/data/remote/ChatClient.kt` — interface + `LlmEngineKind`
- Modify: `app/src/main/java/com/artha/kirana/data/remote/LlmHttpClient.kt` — implement `ChatClient`
- Create: `app/src/main/java/com/artha/kirana/data/remote/dto/OpenRouterModels.kt` — request DTOs + pure body builder
- Create: `app/src/main/java/com/artha/kirana/data/remote/CloudChatClient.kt` — OpenRouter text client
- Create: `app/src/main/java/com/artha/kirana/data/remote/FallbackChatClient.kt` — cloud→local decorator + engine StateFlow
- Create: `app/src/main/java/com/artha/kirana/di/LlmBindModule.kt` — `@Binds ChatClient → FallbackChatClient`
- Modify: `app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt` + `LlmEngine.kt` — inject `ChatClient`
- Test: `app/src/test/java/com/artha/kirana/data/remote/OpenRouterBodyTest.kt`, `FallbackChatClientTest.kt`

**Part B — Cloud OCR bill scan**
- Create: `app/src/main/java/com/artha/kirana/util/ImageUtils.kt` — capture URI + base64 (ported)
- Create: `app/src/main/java/com/artha/kirana/data/remote/CloudVisionClient.kt` — OpenRouter vision + pure `mapBill`
- Create: `app/src/main/java/com/artha/kirana/domain/model/ParsedPurchaseItem.kt` + `ParsedBill.kt`
- Create: `app/src/main/java/com/artha/kirana/domain/repository/PurchaseRepository.kt` + `data/repository/PurchaseRepositoryImpl.kt`
- Modify: `app/src/main/java/com/artha/kirana/di/RepositoryModule.kt` — bind `PurchaseRepository`
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/LogPurchaseUseCase.kt`
- Create: `app/src/main/res/xml/file_paths.xml` + modify `AndroidManifest.xml` (FileProvider)
- Create: `app/src/main/java/com/artha/kirana/ui/scan/BillScanViewModel.kt` + `BillScanScreen.kt`
- Modify: `app/src/main/java/com/artha/kirana/ui/ArthaApp.kt` — `scan` route + entry point
- Test: `app/src/test/java/com/artha/kirana/data/remote/CloudVisionMapTest.kt`, `domain/usecase/LogPurchaseUseCaseTest.kt`

**Part C — UI polish**
- Create: `app/src/main/java/com/artha/kirana/util/CurrencyFormat.kt` — `asRupees()`
- Create: `app/src/main/java/com/artha/kirana/ui/common/EngineBadge.kt`
- Modify: relevant ViewModels/screens to surface the badge + apply `asRupees()`
- Test: `app/src/test/java/com/artha/kirana/util/CurrencyFormatTest.kt`

**Part D — Docs**
- Modify: `CLAUDE.md` §1, `docs/STATUS.md`, `HANDOFF.md`

---

# PART A — Cloud chat seam (DO FIRST)

## Task A1: Wire keys.properties → BuildConfig

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the Properties import at the very top of the file**

At the top of `app/build.gradle.kts` (above the `plugins {` block), add:

```kotlin
import java.util.Properties
```

- [ ] **Step 2: Load keys.properties just inside the `android {` block**

Immediately after the line `android {`, add:

```kotlin
    val keysFile = rootProject.file("keys.properties")
    val keys = Properties().apply {
        if (keysFile.exists()) keysFile.inputStream().use { load(it) }
    }
```

- [ ] **Step 3: Add the BuildConfig fields in `defaultConfig`**

In `defaultConfig { ... }`, right after the existing `buildConfigField("boolean", "VOICE_ENABLED", "true")` line, add:

```kotlin
        buildConfigField("String", "OPENROUTER_KEY", "\"${keys.getProperty("OPENROUTER_KEY", "")}\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"${keys.getProperty("OPENROUTER_MODEL", "anthropic/claude-haiku-4.5")}\"")
        buildConfigField("String", "OPENROUTER_VISION_MODEL", "\"${keys.getProperty("OPENROUTER_VISION_MODEL", "anthropic/claude-haiku-4.5")}\"")
        buildConfigField("boolean", "FORCE_LOCAL_LLM", "false")
```

- [ ] **Step 4: Verify it compiles and the field exists**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Do NOT print the key value anywhere.)

Then confirm the generated field exists (value redacted by grepping only the field name):
Run: `grep -rl "OPENROUTER_KEY" app/build/generated/source/buildConfig/`
Expected: a `BuildConfig.java` path is printed.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: read OpenRouter keys.properties into BuildConfig"
```

---

## Task A2: ChatClient interface + LlmHttpClient implements it

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/remote/ChatClient.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/remote/LlmHttpClient.kt`

- [ ] **Step 1: Create the interface**

Create `app/src/main/java/com/artha/kirana/data/remote/ChatClient.kt`:

```kotlin
package com.artha.kirana.data.remote

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/** Which backend answered the most recent [ChatClient.chat] call. Surfaced as a UI badge. */
enum class LlmEngineKind { CLOUD, ON_DEVICE, NONE }

/**
 * The single LLM chokepoint. [IntentRouter]/[LlmEngine] depend on this, not on a concrete client,
 * so the app can be cloud-primary with a local fallback by swapping the bound implementation.
 */
interface ChatClient {
    /** Sends a system+user turn; returns the assistant content or throws [LlmUnavailableException]. */
    suspend fun chat(system: String, user: String, responseFormat: JsonElement? = null): String

    /** True when at least one backend is plausibly reachable (used for graceful degradation). */
    suspend fun health(): Boolean

    /** The backend that answered the most recent [chat]. Read by the engine badge. */
    val engine: StateFlow<LlmEngineKind>
}
```

- [ ] **Step 2: Make LlmHttpClient implement ChatClient**

In `app/src/main/java/com/artha/kirana/data/remote/LlmHttpClient.kt`:

Change the class declaration from:

```kotlin
@Singleton
class LlmHttpClient @Inject constructor(
    private val client: HttpClient,
) {
```

to:

```kotlin
@Singleton
class LlmHttpClient @Inject constructor(
    private val client: HttpClient,
) : ChatClient {
```

Add `override` to the two interface methods. Change `suspend fun health()` → `override suspend fun health()` and `suspend fun chat(...)` → `override suspend fun chat(...)`.

Then add the `engine` property (constant ON_DEVICE — the local client always answers as on-device; the fallback owns the real flow). Add these imports:

```kotlin
import com.artha.kirana.data.remote.LlmEngineKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
```

and inside the class body (e.g. above `health()`):

```kotlin
    override val engine = MutableStateFlow(LlmEngineKind.ON_DEVICE).asStateFlow()
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (Nothing injects `ChatClient` yet — `IntentRouter`/`LlmEngine` still inject the concrete `LlmHttpClient`, which now also IS a `ChatClient`. No behavior change.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/remote/ChatClient.kt app/src/main/java/com/artha/kirana/data/remote/LlmHttpClient.kt
git commit -m "feat: add ChatClient interface; LlmHttpClient implements it (local)"
```

---

## Task A3: OpenRouter request DTOs + pure body builder (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/remote/dto/OpenRouterModels.kt`
- Test: `app/src/test/java/com/artha/kirana/data/remote/OpenRouterBodyTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/artha/kirana/data/remote/OpenRouterBodyTest.kt`:

```kotlin
package com.artha.kirana.data.remote

import com.artha.kirana.data.remote.dto.buildOpenRouterTextRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenRouterBodyTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test
    fun `json_schema response format is translated to json_object`() {
        // Caller passes a llama.cpp json_schema envelope (what LlmEngine builds).
        val schema = buildJsonObject { put("type", "json_schema") }
        val req = buildOpenRouterTextRequest("the-model", "sys", "usr", schema)
        val obj = json.encodeToJsonElement(req).jsonObject
        val rf = obj["response_format"]!!.jsonObject
        assertEquals("\"json_object\"", rf["type"].toString())
    }

    @Test
    fun `null response format yields no response_format field`() {
        val req = buildOpenRouterTextRequest("the-model", "sys", "usr", null)
        val obj = json.encodeToJsonElement(req).jsonObject
        assertNull(obj["response_format"])
    }

    @Test
    fun `model and messages are populated`() {
        val req = buildOpenRouterTextRequest("the-model", "sys", "usr", null)
        assertEquals("the-model", req.model)
        assertEquals(2, req.messages.size)
        assertEquals("system", req.messages[0].role)
        assertEquals("usr", req.messages[1].content)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.remote.OpenRouterBodyTest"`
Expected: FAIL — `buildOpenRouterTextRequest` unresolved.

- [ ] **Step 3: Write the DTOs + builder**

Create `app/src/main/java/com/artha/kirana/data/remote/dto/OpenRouterModels.kt`:

```kotlin
package com.artha.kirana.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** OpenAI-compatible request for OpenRouter chat-completions (text path). */
@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: JsonObject? = null,
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String,
)

/**
 * Pure builder for the text request. The local path passes a llama.cpp json_schema envelope;
 * OpenRouter/Claude can't take that, so any non-null [responseFormat] is collapsed to
 * `{"type":"json_object"}` and we rely on the system prompt + JsonParser.extractJson. A null
 * [responseFormat] omits the field entirely.
 */
fun buildOpenRouterTextRequest(
    model: String,
    system: String,
    user: String,
    responseFormat: JsonElement?,
): OpenRouterRequest = OpenRouterRequest(
    model = model,
    messages = listOf(
        OpenRouterMessage("system", system),
        OpenRouterMessage("user", user),
    ),
    responseFormat = if (responseFormat != null) {
        JsonObject(mapOf("type" to JsonPrimitive("json_object")))
    } else {
        null
    },
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.remote.OpenRouterBodyTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/remote/dto/OpenRouterModels.kt app/src/test/java/com/artha/kirana/data/remote/OpenRouterBodyTest.kt
git commit -m "feat: OpenRouter request DTOs + json_schema->json_object body builder (TDD)"
```

---

## Task A4: CloudChatClient (OpenRouter text)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/remote/CloudChatClient.kt`

> No new unit test here: the request-body translation is already covered in A3, and the HTTP call itself is integration-only (verified on-device). The class stays thin — pure logic lives in `buildOpenRouterTextRequest`.

- [ ] **Step 1: Write CloudChatClient**

Create `app/src/main/java/com/artha/kirana/data/remote/CloudChatClient.kt`:

```kotlin
package com.artha.kirana.data.remote

import com.artha.kirana.BuildConfig
import com.artha.kirana.data.remote.dto.ChatCompletionResponse
import com.artha.kirana.data.remote.dto.buildOpenRouterTextRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud parser backed by OpenRouter (OpenAI-compatible) → Claude Haiku 4.5. Drop-in for the local
 * [LlmHttpClient]: same [chat] contract, but a frontier model parses our schema more reliably than
 * the on-device 3B. Throws on any failure (blank key / non-2xx / blank body) so [FallbackChatClient]
 * falls back to local. Uses a short per-request timeout so a slow/no-network cloud drops fast.
 */
@Singleton
class CloudChatClient @Inject constructor(
    private val client: HttpClient,
) {
    private val apiKey: String get() = BuildConfig.OPENROUTER_KEY
    private val model: String get() = BuildConfig.OPENROUTER_MODEL

    suspend fun chat(system: String, user: String, responseFormat: JsonElement? = null): String {
        if (apiKey.isBlank()) throw LlmUnavailableException(null)
        val response = client.post("$BASE_URL/chat/completions") {
            timeout { requestTimeoutMillis = CLOUD_TIMEOUT_MS }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://artha.kirana")
            header("X-Title", "Artha Kirana")
            setBody(buildOpenRouterTextRequest(model, system, user, responseFormat))
        }
        if (!response.status.isSuccess()) {
            throw LlmUnavailableException(RuntimeException("OpenRouter ${response.status.value}"))
        }
        val content = response.body<ChatCompletionResponse>().choices.firstOrNull()?.message?.content
        return content?.takeIf { it.isNotBlank() }
            ?: throw LlmUnavailableException(null)
    }

    /** Cheap readiness check: a key is present. Real failures surface at [chat] and fall back. */
    fun keyPresent(): Boolean = apiKey.isNotBlank()

    private companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val CLOUD_TIMEOUT_MS = 10_000L
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

> Note: `import io.ktor.client.plugins.timeout` requires the `HttpTimeout` plugin, already installed in `NetworkModule`. The `timeout { }` per-request block overrides the shared 60s request timeout for this call.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/remote/CloudChatClient.kt
git commit -m "feat: CloudChatClient (OpenRouter/Haiku, 10s timeout, throws->fallback)"
```

---

## Task A5: FallbackChatClient (cloud→local + engine flow) (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/data/remote/FallbackChatClient.kt`
- Test: `app/src/test/java/com/artha/kirana/data/remote/FallbackChatClientTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/artha/kirana/data/remote/FallbackChatClientTest.kt`:

```kotlin
package com.artha.kirana.data.remote

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FallbackChatClientTest {
    private val cloud = mockk<CloudChatClient>()
    private val local = mockk<LlmHttpClient>()
    private val subject = FallbackChatClient(cloud, local)

    @Test
    fun `cloud success returns cloud content and sets engine CLOUD`() = runTest {
        coEvery { cloud.chat(any(), any(), any()) } returns "CLOUD_ANSWER"
        val out = subject.chat("s", "u", null)
        assertEquals("CLOUD_ANSWER", out)
        assertEquals(LlmEngineKind.CLOUD, subject.engine.value)
    }

    @Test
    fun `cloud failure falls back to local and sets engine ON_DEVICE`() = runTest {
        coEvery { cloud.chat(any(), any(), any()) } throws RuntimeException("timeout")
        coEvery { local.chat(any(), any(), any()) } returns "LOCAL_ANSWER"
        val out = subject.chat("s", "u", null)
        assertEquals("LOCAL_ANSWER", out)
        assertEquals(LlmEngineKind.ON_DEVICE, subject.engine.value)
    }

    @Test
    fun `both fail rethrows LlmUnavailable and sets engine NONE`() = runTest {
        coEvery { cloud.chat(any(), any(), any()) } throws RuntimeException("net")
        coEvery { local.chat(any(), any(), any()) } throws LlmUnavailableException(null)
        assertThrows(LlmUnavailableException::class.java) { runTest { subject.chat("s", "u", null) } }
        assertEquals(LlmEngineKind.NONE, subject.engine.value)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.remote.FallbackChatClientTest"`
Expected: FAIL — `FallbackChatClient` unresolved.

- [ ] **Step 3: Write FallbackChatClient**

Create `app/src/main/java/com/artha/kirana/data/remote/FallbackChatClient.kt`:

```kotlin
package com.artha.kirana.data.remote

import com.artha.kirana.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud-primary [ChatClient] with the on-device llama-server as fallback. Tries [cloud] first; on
 * ANY throwable (timeout / blank key / HTTP error / blank body) it falls back to [local]. If both
 * fail it re-throws [LlmUnavailableException] so callers keep their existing manual-entry path.
 * Publishes which backend answered via [engine] for the UI badge. The debug FORCE_LOCAL_LLM flag
 * short-circuits to local for demoing the offline story.
 */
@Singleton
class FallbackChatClient @Inject constructor(
    private val cloud: CloudChatClient,
    private val local: LlmHttpClient,
) : ChatClient {

    private val _engine = MutableStateFlow(LlmEngineKind.NONE)
    override val engine = _engine.asStateFlow()

    override suspend fun chat(system: String, user: String, responseFormat: JsonElement?): String {
        if (!BuildConfig.FORCE_LOCAL_LLM) {
            try {
                val out = cloud.chat(system, user, responseFormat)
                _engine.value = LlmEngineKind.CLOUD
                return out
            } catch (t: Throwable) {
                Timber.w(t, "Cloud LLM failed, falling back to on-device")
            }
        }
        return try {
            val out = local.chat(system, user, responseFormat)
            _engine.value = LlmEngineKind.ON_DEVICE
            out
        } catch (e: LlmUnavailableException) {
            _engine.value = LlmEngineKind.NONE
            throw e
        }
    }

    override suspend fun health(): Boolean =
        (!BuildConfig.FORCE_LOCAL_LLM && cloud.keyPresent()) || local.health()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.remote.FallbackChatClientTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/data/remote/FallbackChatClient.kt app/src/test/java/com/artha/kirana/data/remote/FallbackChatClientTest.kt
git commit -m "feat: FallbackChatClient (cloud-first, local fallback, engine flow) (TDD)"
```

---

## Task A6: Hilt binding + swap IntentRouter/LlmEngine to ChatClient

**Files:**
- Create: `app/src/main/java/com/artha/kirana/di/LlmBindModule.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt`
- Modify: `app/src/main/java/com/artha/kirana/data/llm/LlmEngine.kt`

- [ ] **Step 1: Create the Hilt binding module**

Create `app/src/main/java/com/artha/kirana/di/LlmBindModule.kt`:

```kotlin
package com.artha.kirana.di

import com.artha.kirana.data.remote.ChatClient
import com.artha.kirana.data.remote.FallbackChatClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmBindModule {
    @Binds
    @Singleton
    abstract fun bindChatClient(impl: FallbackChatClient): ChatClient
}
```

- [ ] **Step 2: Swap IntentRouter's dependency**

In `app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt`:
- Change the import `import com.artha.kirana.data.remote.LlmHttpClient` → `import com.artha.kirana.data.remote.ChatClient`.
- Change the constructor param `private val client: LlmHttpClient,` → `private val client: ChatClient,`.

(No other change — `client.chat(...)` is identical on the interface.)

- [ ] **Step 3: Swap LlmEngine's dependency**

In `app/src/main/java/com/artha/kirana/data/llm/LlmEngine.kt`:
- Change the import `import com.artha.kirana.data.remote.LlmHttpClient` → `import com.artha.kirana.data.remote.ChatClient`.
- Change the constructor param `private val client: LlmHttpClient,` → `private val client: ChatClient,`.

(`client.chat(...)` and `client.health()` are both on the interface — unchanged.)

- [ ] **Step 4: Verify the whole app compiles and unit tests pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests green (including the new A3/A5 tests). Hilt now resolves `ChatClient → FallbackChatClient(CloudChatClient, LlmHttpClient)`.

- [ ] **Step 5: Build the APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/di/LlmBindModule.kt app/src/main/java/com/artha/kirana/data/llm/IntentRouter.kt app/src/main/java/com/artha/kirana/data/llm/LlmEngine.kt
git commit -m "feat: bind ChatClient->FallbackChatClient; route IntentRouter/LlmEngine through cloud-first seam"
```

**Part A is functionally complete.** All four text paths (sale/payment/intent/customer) now go cloud-first with local fallback. On-device re-verification of §18 + Assistant happens after Part B (per the spec's combined gate), but you may smoke-test now if convenient.

---

# PART B — Cloud OCR bill scan

## Task B1: ImageUtils (capture URI + base64)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/util/ImageUtils.kt`

> Ported from the colleague's proven `domain/vision/ImageUtils.kt` (iQOO-hardened ImageDecoder path + BitmapFactory/EXIF fallback). No unit test — it's Android-framework I/O, verified on-device.

- [ ] **Step 1: Create ImageUtils**

Create `app/src/main/java/com/artha/kirana/util/ImageUtils.kt` with the full ported implementation:

```kotlin
package com.artha.kirana.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Camera + image helpers for the bill-scan slice. All I/O is best-effort and NEVER throws —
 * [uriToBase64]/[decodePreview] return null on any failure. Honors EXIF orientation and accepts a
 * manual [extraRotation] for untagged sideways photos.
 */
object ImageUtils {

    private val counter = AtomicInteger(0)

    /** Empty target file under cacheDir/images + a FileProvider content:// Uri (authority must match the manifest). */
    fun newImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "cap_${counter.incrementAndGet()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun uriToBase64(
        context: Context,
        uri: Uri,
        maxDim: Int = 1568,
        quality: Int = 90,
        extraRotation: Int = 0,
    ): String? {
        val upright = decodeScaledUpright(context, uri, maxDim) ?: run {
            Log.w("ArthaScan", "uriToBase64: decode failed for $uri")
            return null
        }
        val extra = ((extraRotation % 360) + 360) % 360
        val bmp = if (extra != 0) {
            val r = rotate(upright, extra)
            if (r !== upright) upright.recycle()
            r
        } else upright
        return try {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), baos)
            bmp.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w("ArthaScan", "uriToBase64: compress/encode failed", t)
            null
        }
    }

    fun decodePreview(context: Context, uri: Uri, maxDim: Int = 640): Bitmap? =
        decodeScaledUpright(context, uri, maxDim)

    private fun decodeScaledUpright(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        try {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val longest = max(info.size.width, info.size.height)
                if (longest > maxDim) {
                    val ratio = maxDim.toFloat() / longest
                    decoder.setTargetSize(
                        max(1, (info.size.width * ratio).toInt()),
                        max(1, (info.size.height * ratio).toInt()),
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w("ArthaScan", "ImageDecoder failed for $uri, trying BitmapFactory", t)
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            var bmp = scaleToMaxDim(decoded, maxDim)
            if (bmp !== decoded) decoded.recycle()
            val deg = exifDegrees(context, uri)
            if (deg != 0) {
                val r = rotate(bmp, deg)
                if (r !== bmp) bmp.recycle()
                bmp = r
            }
            bmp
        } catch (t: Throwable) {
            Log.w("ArthaScan", "BitmapFactory fallback also failed for $uri", t)
            null
        }
    }

    private fun exifDegrees(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Throwable) {
        0
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / (sample * 2) >= maxDim) sample *= 2
        return sample
    }

    private fun scaleToMaxDim(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * ratio).toInt()),
            max(1, (src.height * ratio).toInt()),
            true,
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`androidx.core.content.FileProvider` and `ExifInterface` come from `androidx.core.ktx`, already a dependency. If `ExifInterface` is unresolved, it's in `androidx.exifinterface:exifinterface` — but the colleague used `android.media.ExifInterface` as imported above, which is part of the platform SDK; keep that import.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/artha/kirana/util/ImageUtils.kt
git commit -m "feat: ImageUtils (capture uri + EXIF/downscale/base64, iQOO-hardened)"
```

---

## Task B2: Bill domain models + CloudVisionClient mapping (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/model/ParsedPurchaseItem.kt`
- Create: `app/src/main/java/com/artha/kirana/data/remote/CloudVisionClient.kt`
- Test: `app/src/test/java/com/artha/kirana/data/remote/CloudVisionMapTest.kt`

- [ ] **Step 1: Create the domain models**

Create `app/src/main/java/com/artha/kirana/domain/model/ParsedPurchaseItem.kt`:

```kotlin
package com.artha.kirana.domain.model

/** One editable line item read from a supplier bill (pre-confirmation). */
data class ParsedPurchaseItem(
    val name: String,
    val qty: Double = 1.0,
    val unit: String = "pcs",
    val unitPrice: Double? = null,
    val amount: Double? = null,
)

/** Result of a cloud bill scan: line items + an optional grand total. */
data class ParsedBill(
    val items: List<ParsedPurchaseItem> = emptyList(),
    val total: Double? = null,
)
```

- [ ] **Step 2: Write the failing test for the pure mapper**

Create `app/src/test/java/com/artha/kirana/data/remote/CloudVisionMapTest.kt`:

```kotlin
package com.artha.kirana.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudVisionMapTest {

    @Test
    fun `maps items with defaults and fenced json`() {
        val content = """
            ```json
            {"items":[
              {"name":"Rice","qty":2,"unit":"kg","unitPrice":40,"amount":80},
              {"name":"Soap","unit":"pcs"}
            ],"total":80}
            ```
        """.trimIndent()
        val bill = CloudVisionClient.mapBill(content)
        assertEquals(2, bill.items.size)
        assertEquals("Rice", bill.items[0].name)
        assertEquals(2.0, bill.items[0].qty, 0.0)
        assertEquals(40.0, bill.items[0].unitPrice!!, 0.0)
        // defaults: qty -> 1.0, unit kept, prices null
        assertEquals(1.0, bill.items[1].qty, 0.0)
        assertEquals("pcs", bill.items[1].unit)
        assertNull(bill.items[1].unitPrice)
        assertEquals(80.0, bill.total!!, 0.0)
    }

    @Test
    fun `drops blank or null-named rows`() {
        val content = """{"items":[{"name":""},{"name":"null"},{"name":"Atta","qty":1}],"total":null}"""
        val bill = CloudVisionClient.mapBill(content)
        assertEquals(1, bill.items.size)
        assertEquals("Atta", bill.items[0].name)
        assertNull(bill.total)
    }

    @Test
    fun `unreadable content yields empty bill`() {
        val bill = CloudVisionClient.mapBill("sorry I cannot read this")
        assertTrue(bill.items.isEmpty())
        assertNull(bill.total)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.remote.CloudVisionMapTest"`
Expected: FAIL — `CloudVisionClient` unresolved.

- [ ] **Step 4: Write CloudVisionClient (with the pure `mapBill` in a companion)**

Create `app/src/main/java/com/artha/kirana/data/remote/CloudVisionClient.kt`:

```kotlin
package com.artha.kirana.data.remote

import com.artha.kirana.BuildConfig
import com.artha.kirana.data.remote.dto.ChatCompletionResponse
import com.artha.kirana.domain.model.ParsedBill
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.util.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud bill OCR via OpenRouter's vision endpoint (Claude Haiku 4.5). Sends the JPEG as a base64
 * `data:` URI with an anti-hallucination prompt and parses the line items into [ParsedBill].
 * Throws on failure (blank key / non-2xx / blank) so the ViewModel can show an error (cloud-only —
 * no local vision fallback by decision).
 */
@Singleton
class CloudVisionClient @Inject constructor(
    private val client: HttpClient,
) {
    private val apiKey: String get() = BuildConfig.OPENROUTER_KEY
    private val visionModel: String get() = BuildConfig.OPENROUTER_VISION_MODEL

    suspend fun extractBill(imageBase64: String): ParsedBill {
        if (apiKey.isBlank()) throw LlmUnavailableException(null)
        require(imageBase64.isNotBlank()) { "No image to read." }
        val response = client.post("$BASE_URL/chat/completions") {
            timeout { requestTimeoutMillis = VISION_TIMEOUT_MS }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://artha.kirana")
            header("X-Title", "Artha Kirana")
            setBody(buildVisionBody(visionModel, imageBase64))
        }
        if (!response.status.isSuccess()) {
            throw LlmUnavailableException(RuntimeException("OpenRouter ${response.status.value}"))
        }
        val content = response.body<ChatCompletionResponse>().choices.firstOrNull()?.message?.content
            ?: throw LlmUnavailableException(null)
        return mapBill(content)
    }

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1"
        private const val VISION_TIMEOUT_MS = 30_000L

        private val mapper = Json { ignoreUnknownKeys = true; isLenient = true }

        // Vision request: NO response_format (not all vision models accept it); image as data: URI.
        private fun buildVisionBody(model: String, imageBase64: String): JsonObject = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", BILL_SYSTEM)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", BILL_USER)
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                            }
                        }
                    }
                }
            }
        }

        /** Pure: vision JSON content → [ParsedBill]. Never throws; unreadable → empty bill. */
        fun mapBill(content: String): ParsedBill {
            val jsonStr = JsonParser.extractJson(content) ?: return ParsedBill()
            val root = try {
                mapper.parseToJsonElement(jsonStr).jsonObject
            } catch (t: Throwable) {
                return ParsedBill()
            }
            val items = root["items"]?.let { it as? JsonArray }?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val name = o["name"]?.jsonPrimitive?.contentOrNull()?.trim()
                if (name.isNullOrEmpty() || name.equals("null", true)) return@mapNotNull null
                ParsedPurchaseItem(
                    name = name,
                    qty = o["qty"].asDoubleOrNull() ?: 1.0,
                    unit = o["unit"]?.jsonPrimitive?.contentOrNull()?.trim()?.ifBlank { "pcs" } ?: "pcs",
                    unitPrice = o["unitPrice"].asDoubleOrNull(),
                    amount = o["amount"].asDoubleOrNull(),
                )
            } ?: emptyList()
            return ParsedBill(items = items, total = root["total"].asDoubleOrNull())
        }

        private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content

        private fun kotlinx.serialization.json.JsonElement?.asDoubleOrNull(): Double? {
            val p = (this as? JsonPrimitive) ?: return null
            if (p.content.equals("null", true) || p.content.isBlank()) return null
            return p.content.toDoubleOrNull()
        }

        val BILL_SYSTEM = """
            You read a photo of an Indian wholesaler/grocery BILL or RECEIPT (printed or handwritten)
            and convert it into a strict JSON object. Output JSON ONLY — no markdown, no commentary.

            Schema:
            {"items":[{"name":string,"qty":number,"unit":string,"unitPrice":number|null,"amount":number|null}],"total":number|null}

            Rules:
            - CRITICAL: Read ONLY what is actually written. Transcribe item names EXACTLY as written.
              NEVER invent, guess, or substitute typical grocery items, and do NOT "fill in" a usual
              kirana list. Accuracy over completeness — if a line is illegible, omit it rather than guess.
            - One object per line item printed on the bill. Keep item names short and clean (just the product).
            - qty = quantity bought (default 1). units: kg/किलो->"kg", gram/ग्राम->"g", litre/लीटर/लि->"l",
              packet/पैकेट/नग/piece/pcs->"pcs", dozen/दर्जन->"dozen". Default "pcs".
            - unitPrice = price per unit if shown; amount = the line total (rupees). Use null (not 0) for unknown numbers.
            - total = the grand total / bill amount in rupees if printed, else null.
            - Ignore taxes, shop header, phone numbers and non-item text. If you cannot read any items, return {"items":[],"total":null}.
        """.trimIndent()

        const val BILL_USER =
            "Read this wholesaler/grocery bill or receipt photo and return the line items and total as JSON."
    }
}
```

> Note: `contentOrNull()` simply returns `content` (kotlinx's `JsonPrimitive.content` already strips quotes for strings). The two unused imports (`JsonArray`, `add`, `buildJsonArray`, `jsonArray`) may be flagged — remove any the compiler reports as unused to keep the build clean.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.data.remote.CloudVisionMapTest"`
Expected: PASS (3 tests). Fix any unused-import warnings if the build is configured to fail on them (it is not by default).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/model/ParsedPurchaseItem.kt app/src/main/java/com/artha/kirana/data/remote/CloudVisionClient.kt app/src/test/java/com/artha/kirana/data/remote/CloudVisionMapTest.kt
git commit -m "feat: ParsedBill models + CloudVisionClient (OpenRouter vision, pure mapBill) (TDD)"
```

---

## Task B3: PurchaseRepository

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/repository/PurchaseRepository.kt`
- Create: `app/src/main/java/com/artha/kirana/data/repository/PurchaseRepositoryImpl.kt`
- Modify: `app/src/main/java/com/artha/kirana/di/RepositoryModule.kt`

- [ ] **Step 1: Inspect RepositoryModule to match the existing binding style**

Run: `cat app/src/main/java/com/artha/kirana/di/RepositoryModule.kt`
Expected: a Hilt module that `@Binds` each `*RepositoryImpl` to its interface. Match this exact style below.

- [ ] **Step 2: Create the interface**

Create `app/src/main/java/com/artha/kirana/domain/repository/PurchaseRepository.kt`:

```kotlin
package com.artha.kirana.domain.repository

import com.artha.kirana.data.db.entity.PurchaseEntity

interface PurchaseRepository {
    suspend fun add(purchase: PurchaseEntity): Long
}
```

- [ ] **Step 3: Create the impl**

Create `app/src/main/java/com/artha/kirana/data/repository/PurchaseRepositoryImpl.kt`:

```kotlin
package com.artha.kirana.data.repository

import com.artha.kirana.data.db.dao.PurchasesDao
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.domain.repository.PurchaseRepository
import javax.inject.Inject

class PurchaseRepositoryImpl @Inject constructor(
    private val dao: PurchasesDao,
) : PurchaseRepository {
    override suspend fun add(purchase: PurchaseEntity): Long = dao.insert(purchase)
}
```

- [ ] **Step 4: Bind it in RepositoryModule**

In `app/src/main/java/com/artha/kirana/di/RepositoryModule.kt`, add (matching the style observed in Step 1) the import:

```kotlin
import com.artha.kirana.data.repository.PurchaseRepositoryImpl
import com.artha.kirana.domain.repository.PurchaseRepository
```

and inside the module add a binding method:

```kotlin
    @Binds
    @Singleton
    abstract fun bindPurchaseRepository(impl: PurchaseRepositoryImpl): PurchaseRepository
```

> If `RepositoryModule` is an `object` with `@Provides` rather than an `abstract class` with `@Binds`, instead add a `@Provides fun providePurchaseRepository(dao: PurchasesDao): PurchaseRepository = PurchaseRepositoryImpl(dao)`. Verify which from Step 1 and match it.

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/repository/PurchaseRepository.kt app/src/main/java/com/artha/kirana/data/repository/PurchaseRepositoryImpl.kt app/src/main/java/com/artha/kirana/di/RepositoryModule.kt
git commit -m "feat: PurchaseRepository wrapping PurchasesDao.insert"
```

---

## Task B4: LogPurchaseUseCase (TDD)

**Files:**
- Create: `app/src/main/java/com/artha/kirana/domain/usecase/LogPurchaseUseCase.kt`
- Test: `app/src/test/java/com/artha/kirana/domain/usecase/LogPurchaseUseCaseTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/artha/kirana/domain/usecase/LogPurchaseUseCaseTest.kt`:

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.PurchaseRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LogPurchaseUseCaseTest {
    private val inventory = mockk<InventoryRepository>(relaxed = true)
    private val purchases = mockk<PurchaseRepository>(relaxed = true)
    private val subject = LogPurchaseUseCase(inventory, purchases)

    @Test
    fun `existing item is restocked and cost refreshed`() = runTest {
        val existing = ItemEntity(id = 7, name = "Rice", unit = "kg", costPrice = 30.0, sellPrice = 45.0)
        coEvery { inventory.findByName("Rice") } returns existing
        val updated = slot<ItemEntity>()
        coEvery { inventory.updateItem(capture(updated)) } just Runs

        subject(listOf(ParsedPurchaseItem("Rice", qty = 2.0, unit = "kg", unitPrice = 40.0, amount = 80.0)), supplier = "Acme")

        coVerify { inventory.incrementStock(7, 2.0) }
        assertEquals(40.0, updated.captured.costPrice, 0.0)   // cost refreshed from this purchase
        coVerify { purchases.add(match<PurchaseEntity> { it.itemId == 7L && it.qty == 2.0 && it.cost == 80.0 && it.supplier == "Acme" }) }
    }

    @Test
    fun `unknown item is created then stocked`() = runTest {
        coEvery { inventory.findByName("Maggi") } returns null
        coEvery { inventory.addItem(any()) } returns 42L

        subject(listOf(ParsedPurchaseItem("Maggi", qty = 12.0, unit = "pcs", unitPrice = 10.0, amount = 120.0)), supplier = null)

        coVerify { inventory.addItem(match<ItemEntity> { it.name == "Maggi" && it.unit == "pcs" && it.costPrice == 10.0 }) }
        coVerify { inventory.incrementStock(42L, 12.0) }
        coVerify { purchases.add(match<PurchaseEntity> { it.itemId == 42L && it.cost == 120.0 }) }
    }

    @Test
    fun `cost falls back to unitPrice times qty when amount missing`() = runTest {
        coEvery { inventory.findByName("Sugar") } returns null
        coEvery { inventory.addItem(any()) } returns 5L

        subject(listOf(ParsedPurchaseItem("Sugar", qty = 3.0, unit = "kg", unitPrice = 20.0, amount = null)), supplier = null)

        coVerify { purchases.add(match<PurchaseEntity> { it.cost == 60.0 }) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.LogPurchaseUseCaseTest"`
Expected: FAIL — `LogPurchaseUseCase` unresolved.

- [ ] **Step 3: Write LogPurchaseUseCase**

Create `app/src/main/java/com/artha/kirana/domain/usecase/LogPurchaseUseCase.kt`:

```kotlin
package com.artha.kirana.domain.usecase

import com.artha.kirana.data.db.entity.ItemEntity
import com.artha.kirana.data.db.entity.PurchaseEntity
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.domain.repository.InventoryRepository
import com.artha.kirana.domain.repository.PurchaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Applies a confirmed scanned bill: for each line, resolve-or-create the item, increment stock,
 * refresh its cost from this purchase, and record a [PurchaseEntity]. Returns the number of lines
 * applied. Runs off the main thread.
 */
class LogPurchaseUseCase @Inject constructor(
    private val inventory: InventoryRepository,
    private val purchases: PurchaseRepository,
) {
    suspend operator fun invoke(items: List<ParsedPurchaseItem>, supplier: String?): Int =
        withContext(Dispatchers.IO) {
            var applied = 0
            for (line in items) {
                val name = line.name.trim()
                if (name.isEmpty() || line.qty <= 0.0) continue
                val existing = inventory.findByName(name)
                val itemId: Long = if (existing != null) {
                    if (line.unitPrice != null && line.unitPrice > 0.0) {
                        inventory.updateItem(existing.copy(costPrice = line.unitPrice))
                    }
                    existing.id
                } else {
                    inventory.addItem(
                        ItemEntity(
                            name = name,
                            unit = line.unit,
                            costPrice = line.unitPrice ?: 0.0,
                        ),
                    )
                }
                inventory.incrementStock(itemId, line.qty)
                val cost = line.amount ?: (line.unitPrice?.let { it * line.qty } ?: 0.0)
                purchases.add(
                    PurchaseEntity(
                        itemId = itemId,
                        qty = line.qty,
                        cost = cost,
                        supplier = supplier,
                    ),
                )
                applied++
            }
            applied
        }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.domain.usecase.LogPurchaseUseCaseTest"`
Expected: PASS (3 tests).

> If `ItemEntity`'s constructor requires fields beyond `name`/`unit`/`costPrice` without defaults, the compile will fail — open `ItemEntity.kt` and supply the required fields (per CLAUDE.md §4 the entity has defaults for all but `name`, so this should compile as written).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/domain/usecase/LogPurchaseUseCase.kt app/src/test/java/com/artha/kirana/domain/usecase/LogPurchaseUseCaseTest.kt
git commit -m "feat: LogPurchaseUseCase (resolve-or-create, restock, record purchase) (TDD)"
```

---

## Task B5: FileProvider (manifest + file_paths.xml)

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create the file paths config**

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="images" path="images/" />
</paths>
```

- [ ] **Step 2: Add the FileProvider to the manifest**

In `app/src/main/AndroidManifest.xml`, inside `<application>` (e.g. right after the existing `androidx.startup` provider), add:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 3: Verify it builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (manifest merges; authority `com.artha.kirana.fileprovider` matches `ImageUtils.newImageUri`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat: FileProvider for camera capture (authority .fileprovider, cache/images)"
```

---

## Task B6: BillScanViewModel + BillScanScreen + nav entry

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/scan/BillScanViewModel.kt`
- Create: `app/src/main/java/com/artha/kirana/ui/scan/BillScanScreen.kt`
- Modify: `app/src/main/java/com/artha/kirana/ui/ArthaApp.kt`

- [ ] **Step 1: Write the ViewModel**

Create `app/src/main/java/com/artha/kirana/ui/scan/BillScanViewModel.kt`:

```kotlin
package com.artha.kirana.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artha.kirana.data.remote.CloudVisionClient
import com.artha.kirana.domain.model.ParsedBill
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.domain.usecase.LogPurchaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data object Reading : ScanUiState
    data class Review(val items: List<ParsedPurchaseItem>, val supplier: String) : ScanUiState
    data class Error(val message: String) : ScanUiState
    data class Done(val count: Int) : ScanUiState
}

@HiltViewModel
class BillScanViewModel @Inject constructor(
    private val vision: CloudVisionClient,
    private val logPurchase: LogPurchaseUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state = _state.asStateFlow()

    /** Called once per capture with the base64 JPEG. Extracts the bill via the cloud. */
    fun onImageCaptured(base64: String) {
        if (_state.value is ScanUiState.Reading) return
        _state.value = ScanUiState.Reading
        viewModelScope.launch {
            try {
                val bill: ParsedBill = vision.extractBill(base64)
                _state.value = if (bill.items.isEmpty()) {
                    ScanUiState.Error("Padha nahi ja saka — saaf, seedhi photo lein (poora bill frame mein).")
                } else {
                    ScanUiState.Review(bill.items, supplier = "")
                }
            } catch (t: Throwable) {
                Timber.w(t, "Bill extract failed")
                _state.value = ScanUiState.Error(t.message ?: "Could not read the bill.")
            }
        }
    }

    fun updateItem(index: Int, item: ParsedPurchaseItem) {
        val cur = _state.value as? ScanUiState.Review ?: return
        _state.value = cur.copy(items = cur.items.toMutableList().also { it[index] = item })
    }

    fun removeItem(index: Int) {
        val cur = _state.value as? ScanUiState.Review ?: return
        _state.value = cur.copy(items = cur.items.toMutableList().also { it.removeAt(index) })
    }

    fun setSupplier(supplier: String) {
        val cur = _state.value as? ScanUiState.Review ?: return
        _state.value = cur.copy(supplier = supplier)
    }

    fun confirm() {
        val cur = _state.value as? ScanUiState.Review ?: return
        viewModelScope.launch {
            val count = logPurchase(cur.items, cur.supplier.ifBlank { null })
            _state.value = ScanUiState.Done(count)
        }
    }

    fun reset() { _state.value = ScanUiState.Idle }
}
```

- [ ] **Step 2: Write the screen (system-camera capture with iQOO hardening + editable review)**

Create `app/src/main/java/com/artha/kirana/ui/scan/BillScanScreen.kt`:

```kotlin
package com.artha.kirana.ui.scan

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.artha.kirana.domain.model.ParsedPurchaseItem
import com.artha.kirana.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScanScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val vm: BillScanViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    // Pending-capture gate: the iQOO camera can cold-restart this activity and lose rememberSaveable,
    // so the single source of truth for "a capture is waiting" is a Uri persisted in SharedPreferences.
    val prefs = remember {
        context.getSharedPreferences("artha_scan", Context.MODE_PRIVATE)
    }
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    fun processCapture() {
        if (state is ScanUiState.Reading) return
        val pendingStr = prefs.getString("pending_uri", null) ?: return
        val uri = captureUri ?: Uri.parse(pendingStr)
        if (captureUri == null) captureUri = uri
        Log.i("ArthaScan", "processCapture: ingesting $uri")
        val b64 = ImageUtils.uriToBase64(context, uri, maxDim = 1568, quality = 90)
        if (b64 != null) {
            prefs.edit().remove("pending_uri").apply()
            vm.onImageCaptured(b64)
        } else {
            Log.w("ArthaScan", "processCapture: uriToBase64 returned NULL for $uri")
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        Log.i("ArthaScan", "TakePicture callback ok=$ok")
        processCapture()  // ignore ok: this vivo returns false on success
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) processCapture()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { processCapture() }  // cold-restart fallback

    LaunchedEffect(state) {
        if (state is ScanUiState.Done) onDone()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("बिल स्कैन / Scan Bill") }) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = {
                    val uri = ImageUtils.newImageUri(context)
                    captureUri = uri
                    prefs.edit().putString("pending_uri", uri.toString()).apply()
                    takePicture.launch(uri)
                },
                enabled = state !is ScanUiState.Reading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Photo lein / Capture")
            }

            when (val s = state) {
                is ScanUiState.Reading -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Padha ja raha hai… (cloud)")
                }
                is ScanUiState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ScanUiState.Review -> ReviewSection(
                    items = s.items,
                    supplier = s.supplier,
                    onSupplier = vm::setSupplier,
                    onItem = vm::updateItem,
                    onRemove = vm::removeItem,
                    onConfirm = vm::confirm,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun ReviewSection(
    items: List<ParsedPurchaseItem>,
    supplier: String,
    onSupplier: (String) -> Unit,
    onItem: (Int, ParsedPurchaseItem) -> Unit,
    onRemove: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    OutlinedTextField(
        value = supplier,
        onValueChange = onSupplier,
        label = { Text("Supplier (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    items.forEachIndexed { i, item ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = item.name,
                onValueChange = { onItem(i, item.copy(name = it)) },
                label = { Text("Item") },
                modifier = Modifier.width(140.dp),
            )
            OutlinedTextField(
                value = if (item.qty == item.qty.toLong().toDouble()) item.qty.toLong().toString() else item.qty.toString(),
                onValueChange = { onItem(i, item.copy(qty = it.toDoubleOrNull() ?: item.qty)) },
                label = { Text("Qty") },
                modifier = Modifier.width(80.dp),
            )
            OutlinedTextField(
                value = item.amount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "",
                onValueChange = { onItem(i, item.copy(amount = it.toDoubleOrNull())) },
                label = { Text("₹") },
                modifier = Modifier.width(90.dp),
            )
            IconButton(onClick = { onRemove(i) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
    Button(
        onClick = onConfirm,
        enabled = items.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Stock mein daalein / Confirm (${items.size})")
    }
}
```

- [ ] **Step 3: Add the nav route + entry point in ArthaApp.kt**

In `app/src/main/java/com/artha/kirana/ui/ArthaApp.kt`:

Add the import:

```kotlin
import com.artha.kirana.ui.scan.BillScanScreen
import androidx.compose.material.icons.filled.CameraAlt
```

Add a route constant near `ROUTE_SALE_ENTRY`:

```kotlin
const val ROUTE_SCAN = "scan"
```

Register the composable in the `NavHost` (next to the `ROUTE_SALE_ENTRY` entry):

```kotlin
            composable(ROUTE_SCAN) {
                BillScanScreen(onDone = { navController.popBackStack() })
            }
```

Surface it from Home: change the Home `ExtendedFloatingActionButton` block so it offers Scan in addition to New Sale. Replace the existing `floatingActionButton = { ... }` block with:

```kotlin
        floatingActionButton = {
            if (currentRoute == TopDest.Home.route) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(ROUTE_SCAN) },
                        icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                        text = { Text("Scan Bill") },
                    )
                    ExtendedFloatingActionButton(
                        onClick = { navController.navigate(ROUTE_SALE_ENTRY) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("New Sale") },
                    )
                }
            }
        },
```

Add the needed imports for `Column`/`Arrangement` if not present:

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
```

- [ ] **Step 4: Verify it builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/scan/ app/src/main/java/com/artha/kirana/ui/ArthaApp.kt
git commit -m "feat: bill-scan screen (system camera + iQOO hardening) + Home entry + nav route"
```

- [ ] **Step 6: On-device smoke test (camera path can't be unit-tested)**

Run: `./gradlew :app:installDebug && adb -s 10BFBG0CEL001DB shell am start -n com.artha.kirana/.MainActivity`
Then manually: Home → Scan Bill → grant CAMERA → photograph a printed receipt → confirm items appear in the review list → Confirm → check Inventory restocked. (Full owner verification is the combined A+B gate.)

---

# PART C — UI polish

## Task C1: asRupees() (TDD) + apply

**Files:**
- Create: `app/src/main/java/com/artha/kirana/util/CurrencyFormat.kt`
- Test: `app/src/test/java/com/artha/kirana/util/CurrencyFormatTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/artha/kirana/util/CurrencyFormatTest.kt`:

```kotlin
package com.artha.kirana.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyFormatTest {
    @Test fun `thousands group`() = assertEquals("₹1,234", 1234.0.asRupees())
    @Test fun `lakh grouping uses indian system`() = assertEquals("₹12,40,000", 1240000.0.asRupees())
    @Test fun `small value`() = assertEquals("₹50", 50.0.asRupees())
    @Test fun `drops trailing zero decimals`() = assertEquals("₹99", 99.0.asRupees())
    @Test fun `keeps meaningful decimals`() = assertEquals("₹2.50", 2.5.asRupees())
    @Test fun `zero`() = assertEquals("₹0", 0.0.asRupees())
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.util.CurrencyFormatTest"`
Expected: FAIL — `asRupees` unresolved.

- [ ] **Step 3: Implement asRupees()**

Create `app/src/main/java/com/artha/kirana/util/CurrencyFormat.kt`:

```kotlin
package com.artha.kirana.util

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Formats a rupee amount with Indian digit grouping (₹12,40,000): the first group is 3 digits,
 * every group after is 2. Drops the decimal part when it's a whole number, else shows 2 places.
 */
fun Double.asRupees(): String {
    val negative = this < 0
    val rounded = BigDecimal(abs(this)).setScale(2, RoundingMode.HALF_UP)
    val whole = rounded.toBigInteger().toString()
    val fraction = rounded.remainder(BigDecimal.ONE)

    val grouped = groupIndian(whole)
    val decimals = if (fraction.compareTo(BigDecimal.ZERO) == 0) {
        ""
    } else {
        "." + rounded.toPlainString().substringAfter('.')
    }
    val sign = if (negative) "-" else ""
    return "$sign₹$grouped$decimals"
}

private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3)
    val sb = StringBuilder()
    var count = 0
    for (i in rest.length - 1 downTo 0) {
        sb.append(rest[i])
        count++
        if (count % 2 == 0 && i != 0) sb.append(',')
    }
    return sb.reverse().toString() + "," + last3
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.artha.kirana.util.CurrencyFormatTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Apply asRupees() at amount render sites**

Find where amounts are currently rendered (likely `"₹$amount"` or `"₹${...}"`):

Run: `grep -rn "₹" app/src/main/java/com/artha/kirana/ui/`

For each user-facing amount, replace the manual `"₹$x"` with `x.asRupees()` (import `com.artha.kirana.util.asRupees`). Do this for Home summary, P&L metric cards, Khata balances, and the sale confirm card. Keep changes mechanical — one screen at a time, rebuilding between.

- [ ] **Step 6: Verify it builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/artha/kirana/util/CurrencyFormat.kt app/src/test/java/com/artha/kirana/util/CurrencyFormatTest.kt app/src/main/java/com/artha/kirana/ui/
git commit -m "feat: asRupees() Indian rupee grouping + apply across screens (TDD)"
```

---

## Task C2: Engine badge

**Files:**
- Create: `app/src/main/java/com/artha/kirana/ui/common/EngineBadge.kt`
- Modify: `app/src/main/java/com/artha/kirana/ui/assistant/AssistantViewModel.kt` (expose engine)
- Modify: `app/src/main/java/com/artha/kirana/ui/assistant/AssistantScreen.kt` (show badge)

- [ ] **Step 1: Create the badge composable**

Create `app/src/main/java/com/artha/kirana/ui/common/EngineBadge.kt`:

```kotlin
package com.artha.kirana.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.artha.kirana.data.remote.LlmEngineKind

/** Small chip showing which LLM backend answered the most recent request. Hidden when NONE. */
@Composable
fun EngineBadge(engine: LlmEngineKind, modifier: Modifier = Modifier) {
    val label = when (engine) {
        LlmEngineKind.CLOUD -> "☁ Cloud"
        LlmEngineKind.ON_DEVICE -> "📱 On-device"
        LlmEngineKind.NONE -> return
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
```

- [ ] **Step 2: Expose the engine flow from AssistantViewModel**

In `AssistantViewModel.kt`, add a constructor injection of `ChatClient` and re-expose its `engine`:

Add imports:

```kotlin
import com.artha.kirana.data.remote.ChatClient
```

Add to the constructor params (e.g. after `whisper`):

```kotlin
    private val chatClient: ChatClient,
```

Add a public property in the class body:

```kotlin
    val engine = chatClient.engine
```

- [ ] **Step 3: Show the badge in AssistantScreen**

In `AssistantScreen.kt`, collect the engine flow and place an `EngineBadge` near the top of the chat (e.g. in the top bar or above the message list):

```kotlin
import com.artha.kirana.ui.common.EngineBadge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
// ...
val engine by vm.engine.collectAsStateWithLifecycle()
EngineBadge(engine)
```

Place `EngineBadge(engine)` where the existing screen has its header/title area (match the existing layout; if the screen already has a top `Row`/`Column`, drop it in there).

- [ ] **Step 4: Verify it builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/artha/kirana/ui/common/EngineBadge.kt app/src/main/java/com/artha/kirana/ui/assistant/
git commit -m "feat: engine badge (cloud / on-device) on the Assistant"
```

---

# PART D — Docs + final verification

## Task D1: Update CLAUDE.md §1 framing + STATUS + HANDOFF

**Files:**
- Modify: `CLAUDE.md` (§1 ARCHITECTURE NOTE)
- Modify: `docs/STATUS.md`
- Modify: `HANDOFF.md`

- [ ] **Step 1: Amend CLAUDE.md §1**

In `CLAUDE.md` §1, under the ARCHITECTURE NOTE, add a paragraph noting the pivot:

```markdown
### ARCHITECTURE NOTE — Cloud-primary LLM (2026-06-14 pivot)
The text LLM now runs **cloud-primary**: OpenRouter → Claude Haiku 4.5 parses sales / payments /
intent / customer-names, with the on-device llama-server (Qwen 2.5 3B) as automatic **fallback**
(cloud failure / timeout / blank key / FORCE_LOCAL_LLM → local). Bill OCR is cloud-vision (Haiku).
This amends the "nothing leaves the phone" pitch for demo reliability; the local fallback preserves
an offline story ("works offline, just slower/less accurate"). Keys live in gitignored
`keys.properties` → BuildConfig. The seam is `ChatClient` (data/remote/); one Hilt binding flips
cloud↔local.
```

- [ ] **Step 2: Update STATUS.md**

In `docs/STATUS.md`, change the "⚠️ ACTIVE PIVOT" section to reflect that the pivot is now BUILT (cloud seam + cloud OCR + UI polish), pending owner on-device verification. Note the new files and the A+B gate.

- [ ] **Step 3: Update HANDOFF.md**

In `HANDOFF.md`, update the State section: cloud-primary seam built (`ChatClient`/`CloudChatClient`/`FallbackChatClient`), cloud bill-scan built (`BillScanScreen`/`CloudVisionClient`/`LogPurchaseUseCase`), badge + asRupees. Update the "Get running" note that the cloud path needs `keys.properties` (already present) and works without the local server when online.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md docs/STATUS.md HANDOFF.md
git commit -m "docs: record cloud-LLM pivot as built (seam + OCR + polish)"
```

## Task D2: Full regression + on-device gate

- [ ] **Step 1: Full unit suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — all tests green (existing 73 + new: OpenRouterBody 3, Fallback 3, CloudVisionMap 3, LogPurchase 3, CurrencyFormat 6).

- [ ] **Step 2: Clean build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install + on-device A gate (owner-driven)**

Start the local fallback server (so fallback is exercisable): `./scripts/start-llama-server.sh`
Install: `./gradlew :app:installDebug`

Verify on the iQOO (`10BFBG0CEL001DB`):
- §18: all 5 sale cases parse correctly via the Assistant / Sale Entry (now cloud-first). Badge shows **☁ Cloud**.
- Payment, customer-query, and analytics intents still work.
- Toggle airplane mode ON (or set `FORCE_LOCAL_LLM=true` and rebuild) → a sale still parses via local; badge shows **📱 On-device**.

- [ ] **Step 4: On-device B gate (owner-driven)**

- Home → Scan Bill → photograph a real supplier receipt → items appear in the review list → edit if needed → Confirm → Inventory shows the restock; a purchase was logged.

- [ ] **Step 5: Final commit (if any doc tweaks from verification)**

```bash
git add -A
git commit -m "chore: cloud-LLM pivot verified on-device (A+B gates)"
```

---

## Self-Review (completed by plan author)

- **Spec coverage:** A (seam: A1 keys, A2 interface, A3 DTO/builder, A4 CloudChatClient, A5 FallbackChatClient, A6 Hilt+swap) ✓; B (B1 ImageUtils, B2 vision+models, B3 PurchaseRepository, B4 LogPurchaseUseCase, B5 FileProvider, B6 screen+nav) ✓; C (C1 asRupees, C2 badge) ✓; cross-cutting build/test/docs (A1, D1, D2) ✓. Combined A+B on-device gate (D2) matches the spec's "build A+B then verify" decision ✓.
- **Placeholder scan:** no TBD/TODO; every code step has complete code; the two "inspect existing style" steps (B3 Step 1, C1 Step 5) are discovery for mechanical edits, not deferred logic.
- **Type consistency:** `ChatClient.chat/health/engine`, `LlmEngineKind`, `buildOpenRouterTextRequest`, `ParsedBill`/`ParsedPurchaseItem`, `CloudVisionClient.mapBill`, `LogPurchaseUseCase(items, supplier)`, `ScanUiState` — all referenced consistently across tasks.

## Known risks / notes for the implementer

- **mockk on concrete classes:** `FallbackChatClientTest` mocks the concrete `CloudChatClient`/`LlmHttpClient`. mockk handles this (no `open` needed on JVM). If a "final class" error appears, add `io.mockk` is already a dep — ensure `mockk(relaxed = false)` and `coEvery` for suspend funs.
- **`FORCE_LOCAL_LLM` branch** is verified by build + the on-device gate (D2 Step 3), not a unit test (it reads BuildConfig directly).
- **Kotlinx JSON imports in CloudVisionClient:** remove any import the compiler flags unused (the builder uses `buildJsonObject`/`putJsonArray`/`addJsonObject`/`put`/`putJsonObject`; the mapper uses `jsonObject`/`jsonPrimitive`/`JsonObject`/`JsonArray`/`JsonPrimitive`).
- **Do NOT** `adb uninstall` to reset (wipes the 181MB whisper model). Use `adb -s 10BFBG0CEL001DB shell run-as com.artha.kirana rm databases/artha.db*`.
- Toolchain pinned (AGP 8.13 / Gradle 8.13 / JDK 17 / Vico 2.1.3 / Room v3). Don't upgrade.

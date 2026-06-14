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
        try {
            subject.chat("s", "u", null)
            throw AssertionError("Expected LlmUnavailableException but no exception was thrown")
        } catch (e: LlmUnavailableException) {
            // expected
        }
        assertEquals(LlmEngineKind.NONE, subject.engine.value)
    }
}

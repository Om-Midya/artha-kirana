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

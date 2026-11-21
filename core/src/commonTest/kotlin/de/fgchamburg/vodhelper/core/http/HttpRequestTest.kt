package de.fgchamburg.vodhelper.core.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HttpRequestTest {
    @Test
    fun `formEncode keeps order and encodes reserved characters`() {
        val encoded = formEncode(
            listOf("client_id" to "abc", "scopes" to "user:read:broadcast", "x" to "a b&c=d/é"),
        )
        assertEquals("client_id=abc&scopes=user%3Aread%3Abroadcast&x=a%20b%26c%3Dd%2F%C3%A9", encoded)
    }

    @Test
    fun `form sets method, content type and body`() {
        val request = HttpRequest.form("https://example.com/token", listOf("a" to "1", "b" to "2 3"))
        assertEquals("POST", request.method)
        assertEquals("https://example.com/token", request.url)
        assertEquals("Content-Type" to "application/x-www-form-urlencoded", request.headers.first())
        assertEquals("a=1&b=2%203", request.body)
    }

    @Test
    fun `get has no body`() {
        val request = HttpRequest.get("https://example.com", listOf("X" to "y"))
        assertEquals("GET", request.method)
        assertEquals(null, request.body)
        assertEquals(listOf("X" to "y"), request.headers)
    }

    @Test
    fun `toString hides headers and body`() {
        val request = HttpRequest(
            "POST", "https://example.com/token",
            headers = listOf("Authorization" to "Bearer secret123"),
            body = "refresh_token=secret456",
        )
        val text = request.toString()
        assertEquals("HttpRequest(POST https://example.com/token)", text)
        assertFalse("secret123" in text)
        assertFalse("secret456" in text)
    }
}

package de.fgchamburg.vodhelper.core.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JdkHttpClientTest {
    private fun server(handler: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { ex -> handler(ex); ex.close() }
            start()
        }

    private fun respond(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray()
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    @Test
    fun `sends method, headers and body and returns status and body`() = runBlocking {
        var seenMethod = ""
        var seenType: String? = null
        var seenBody = ""
        val server = server { ex ->
            seenMethod = ex.requestMethod
            seenType = ex.requestHeaders.getFirst("Content-Type")
            seenBody = ex.requestBody.readBytes().decodeToString()
            respond(ex, 201, "created")
        }
        try {
            val url = "http://127.0.0.1:${server.address.port}/token"
            val response = JdkHttpClient().send(HttpRequest.form(url, listOf("a" to "1", "b" to "x y")))
            assertEquals(201, response.status)
            assertEquals("created", response.body)
            assertEquals("POST", seenMethod)
            assertEquals("application/x-www-form-urlencoded", seenType)
            assertEquals("a=1&b=x%20y", seenBody)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `non-2xx is returned not thrown`() = runBlocking {
        val server = server { ex -> respond(ex, 404, "nope") }
        try {
            val response = JdkHttpClient().send(HttpRequest.get("http://127.0.0.1:${server.address.port}/x"))
            assertEquals(404, response.status)
            assertEquals("nope", response.body)
            assertEquals(false, response.isSuccess)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `unreachable server throws NetworkException`() {
        val server = server { ex -> respond(ex, 200, "") }
        val port = server.address.port
        server.stop(0)
        assertFailsWith<NetworkException> {
            runBlocking { JdkHttpClient().send(HttpRequest.get("http://127.0.0.1:$port/")) }
        }
    }
}

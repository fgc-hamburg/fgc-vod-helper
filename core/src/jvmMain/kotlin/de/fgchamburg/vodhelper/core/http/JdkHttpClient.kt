package de.fgchamburg.vodhelper.core.http

import kotlinx.coroutines.future.await
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient as JdkClient
import java.net.http.HttpRequest as JdkRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.CompletionException

/** [HttpClient] on the JDK's built-in client. */
class JdkHttpClient(private val timeout: Duration = Duration.ofSeconds(30)) : HttpClient {
    private val client = JdkClient.newBuilder().connectTimeout(timeout).build()

    override suspend fun send(request: HttpRequest): HttpResponse {
        val uri = URI.create(request.url)
        val publisher = request.body?.let { JdkRequest.BodyPublishers.ofString(it) }
            ?: JdkRequest.BodyPublishers.noBody()
        val builder = JdkRequest.newBuilder(uri).timeout(timeout).method(request.method, publisher)
        request.headers.forEach { (k, v) -> builder.header(k, v) }
        try {
            val response = client.sendAsync(builder.build(), BodyHandlers.ofString()).await()
            return HttpResponse(response.statusCode(), response.body())
        } catch (e: CompletionException) {
            throw (e.cause ?: e).toNetwork(uri)
        } catch (e: IOException) {
            throw e.toNetwork(uri)
        }
    }

    private fun Throwable.toNetwork(uri: URI): Throwable =
        if (this is IOException) NetworkException("Couldn't reach ${uri.host}", this) else this
}

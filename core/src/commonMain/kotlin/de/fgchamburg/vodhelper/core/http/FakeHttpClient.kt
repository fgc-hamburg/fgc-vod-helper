package de.fgchamburg.vodhelper.core.http

/** An [HttpClient] for tests: records requests, answers with [handler] (which may throw [NetworkException]). */
class FakeHttpClient(private val handler: (HttpRequest) -> HttpResponse) : HttpClient {
    private val sent = mutableListOf<HttpRequest>()
    val requests: List<HttpRequest> get() = sent

    override suspend fun send(request: HttpRequest): HttpResponse {
        sent += request
        return handler(request)
    }
}

package de.fgchamburg.vodhelper.core.http

/** Sends HTTP requests. Non-2xx answers are returned; an unreachable server throws [NetworkException]. */
interface HttpClient {
    suspend fun send(request: HttpRequest): HttpResponse
}

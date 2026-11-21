package de.fgchamburg.vodhelper.core.http

data class HttpResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
}

package de.fgchamburg.vodhelper.core.http

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>> = emptyList(),
    val body: String? = null,
) {
    /** Headers and body can hold secrets, so they are never printed. */
    override fun toString(): String = "HttpRequest($method $url)"

    companion object {
        fun get(url: String, headers: List<Pair<String, String>> = emptyList()): HttpRequest =
            HttpRequest("GET", url, headers)

        fun form(
            url: String,
            fields: List<Pair<String, String>>,
            headers: List<Pair<String, String>> = emptyList(),
        ): HttpRequest = HttpRequest(
            "POST", url,
            listOf("Content-Type" to "application/x-www-form-urlencoded") + headers,
            formEncode(fields),
        )
    }
}

private const val HEX = "0123456789ABCDEF"

private fun percentEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
            append(ch)
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }
}

/** `application/x-www-form-urlencoded` body; field order is kept. */
fun formEncode(fields: List<Pair<String, String>>): String =
    fields.joinToString("&") { (k, v) -> percentEncode(k) + "=" + percentEncode(v) }

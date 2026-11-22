package com.rajatmalik.commitgenerator.core.ports

import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import com.rajatmalik.commitgenerator.core.domain.CwResult

enum class HttpMethod {
    GET,
    POST,
    DELETE,
}

data class HttpRequestData(
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, String>,
    val body: String?,
    val timeoutMs: Int,
    val signal: CancelSignal,
)

data class HttpResponseData(
    val status: Int,
    val headers: Map<String, String>,
    val text: String,
)

/**
 * The only transport abstraction in the product. Every network request must
 * pass through it so redaction, URL checks, timeouts, redirect policy and
 * cancellation cannot be forgotten by an adapter.
 */
interface HttpClientPort {
    fun send(request: HttpRequestData): CwResult<HttpResponseData>
}

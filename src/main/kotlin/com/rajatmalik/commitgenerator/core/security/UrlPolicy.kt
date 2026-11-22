package com.rajatmalik.commitgenerator.core.security

import com.rajatmalik.commitgenerator.core.domain.CwResult
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.domain.err
import com.rajatmalik.commitgenerator.core.domain.ok
import java.net.URI
import java.net.URISyntaxException

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

private val BLOCKED_HOST_PATTERNS = listOf(
    Regex("^169\\.254\\."), // link-local, including cloud metadata
    Regex("^100\\.100\\.100\\.200$"), // alibaba metadata
    Regex("^fd00:ec2::254$"),
    Regex("^0\\.0\\.0\\.0$"),
    Regex("^\\[?::]?$"),
)

data class AllowedUrl(
    val uri: URI,
    val origin: String,
)

fun isLoopbackHost(hostname: String): Boolean {
    val host = hostname.lowercase()
    return host in LOOPBACK_HOSTS || host.startsWith("127.")
}

fun originOf(uri: URI): String {
    val host = uri.host ?: ""
    val port = if (uri.port == -1) "" else ":${uri.port}"
    return "${uri.scheme.lowercase()}://${host.lowercase()}$port"
}

/**
 * Egress URL policy. HTTPS is mandatory for anything that is not loopback.
 * Embedded credentials, non-HTTP schemes and metadata endpoints are rejected.
 * There is deliberately no certificate-validation bypass anywhere.
 */
fun evaluateUrl(rawUrl: String, providerId: String, localOnly: Boolean = false): CwResult<AllowedUrl> {
    val uri = try {
        URI(rawUrl.trim())
    } catch (_: URISyntaxException) {
        return err(appError(ErrorCode.URL_REJECTED, "$providerId: base URL is not a valid URL.", providerId = providerId))
    }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") {
        return err(appError(ErrorCode.URL_REJECTED, "$providerId: only http(s) base URLs are supported.", providerId = providerId))
    }
    if (!uri.userInfo.isNullOrEmpty()) {
        return err(
            appError(
                ErrorCode.URL_REJECTED,
                "$providerId: base URL must not embed credentials; use the Set API Key action.",
                providerId = providerId,
            ),
        )
    }
    val host = uri.host
    if (host.isNullOrBlank()) {
        return err(appError(ErrorCode.URL_REJECTED, "$providerId: base URL must include a host.", providerId = providerId))
    }
    if (scheme == "http" && !isLoopbackHost(host)) {
        return err(
            appError(
                ErrorCode.URL_REJECTED,
                "$providerId: plain http is only allowed for localhost/loopback endpoints.",
                providerId = providerId,
            ),
        )
    }
    if (BLOCKED_HOST_PATTERNS.any { it.containsMatchIn(host.lowercase()) }) {
        return err(appError(ErrorCode.URL_REJECTED, "$providerId: base URL targets a blocked address.", providerId = providerId))
    }
    if (localOnly && !isLoopbackHost(host)) {
        return err(
            appError(
                ErrorCode.URL_REJECTED,
                "$providerId: local-only mode is enabled, so only loopback endpoints are allowed.",
                providerId = providerId,
            ),
        )
    }

    return ok(AllowedUrl(uri, originOf(uri)))
}

fun joinUrl(baseUrl: String, path: String): String {
    val base = baseUrl.trimEnd('/')
    val suffix = if (path.startsWith('/')) path else "/$path"
    return base + suffix
}

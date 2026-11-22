package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.core.domain.AppError
import com.rajatmalik.commitgenerator.core.domain.ErrorCode
import com.rajatmalik.commitgenerator.core.domain.appError
import com.rajatmalik.commitgenerator.core.security.redactSecrets

private val CONTEXT_OVERFLOW = Regex(
    "context length|context window|too many tokens|maximum context|token limit|input is too long|" +
        "prompt is too long|exceeds the maximum|request too large",
    RegexOption.IGNORE_CASE,
)
private val QUOTA = Regex("quota|billing|insufficient|credit|payment|out of budget", RegexOption.IGNORE_CASE)

private fun snippet(detail: String): String? {
    val redacted = redactSecrets(detail).text.replace(Regex("\\s+"), " ").trim()
    return redacted.take(160).ifEmpty { null }
}

/**
 * Maps an HTTP failure to the normalized error taxonomy. Provider text is
 * never placed into `message`; at most a short redacted snippet becomes `cause`.
 */
fun normalizeProviderError(
    status: Int,
    detail: String,
    providerId: String,
    model: String? = null,
): AppError {
    val cause = snippet(detail)
    if (status == 401 || status == 403) {
        if (QUOTA.containsMatchIn(detail)) {
            return appError(
                ErrorCode.QUOTA,
                "The provider rejected the request: quota or billing limit reached.",
                httpStatus = status, providerId = providerId, model = model, cause = cause,
            )
        }
        return appError(
            ErrorCode.AUTH,
            "Authentication failed. Check the API key for this profile.",
            httpStatus = status, providerId = providerId, model = model, cause = cause,
        )
    }
    if (status == 429) {
        if (QUOTA.containsMatchIn(detail)) {
            return appError(
                ErrorCode.QUOTA,
                "The provider reports no remaining quota.",
                httpStatus = status, providerId = providerId, model = model, cause = cause,
            )
        }
        return appError(
            ErrorCode.RATE_LIMIT,
            "The provider is rate limiting requests. Try again shortly.",
            retryable = true, httpStatus = status, providerId = providerId, model = model, cause = cause,
        )
    }
    if (status == 413 || CONTEXT_OVERFLOW.containsMatchIn(detail)) {
        return appError(
            ErrorCode.CONTEXT_OVERFLOW,
            "The change set is too large for this model.",
            httpStatus = status, providerId = providerId, model = model, cause = cause,
        )
    }
    if (status == 404) {
        return appError(
            ErrorCode.BAD_REQUEST,
            "Model \"${model ?: "unknown"}\" was not found on this endpoint.",
            httpStatus = status, providerId = providerId, model = model, cause = cause,
        )
    }
    if (status >= 500) {
        return appError(
            ErrorCode.SERVER,
            "The provider returned a server error.",
            retryable = true, httpStatus = status, providerId = providerId, model = model, cause = cause,
        )
    }
    return appError(
        ErrorCode.BAD_REQUEST,
        "The provider rejected the request (HTTP $status).",
        httpStatus = status, providerId = providerId, model = model, cause = cause,
    )
}

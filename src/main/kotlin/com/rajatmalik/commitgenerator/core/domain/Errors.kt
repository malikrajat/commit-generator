package com.rajatmalik.commitgenerator.core.domain

enum class ErrorCode {
    CONFIG,
    GIT_DISABLED,
    NO_REPOSITORY,
    PROJECT_NOT_TRUSTED,
    NO_CHANGES,
    PROFILE_MISSING,
    PROFILE_INVALID,
    SECRET_MISSING,
    CONSENT_REQUIRED,
    URL_REJECTED,
    AUTH,
    RATE_LIMIT,
    QUOTA,
    TIMEOUT,
    NETWORK,
    TLS,
    BAD_REQUEST,
    CONTEXT_OVERFLOW,
    CONTENT_FILTER,
    SERVER,
    CANCELLED,
    INVALID_RESPONSE,
    WRITE_FAILED,
    INTERNAL,
}

/**
 * User-facing error. [message] is always safe to display: it never contains
 * credentials, prompt text, diff text, or raw provider response bodies.
 */
data class AppError(
    val code: ErrorCode,
    val message: String,
    val retryable: Boolean = false,
    val httpStatus: Int? = null,
    val providerId: String? = null,
    val model: String? = null,
    val requestId: String? = null,
    val cause: String? = null,
) {
    val cancelled: Boolean
        get() = code == ErrorCode.CANCELLED
}

fun appError(
    code: ErrorCode,
    message: String,
    retryable: Boolean = false,
    httpStatus: Int? = null,
    providerId: String? = null,
    model: String? = null,
    requestId: String? = null,
    cause: String? = null,
): AppError = AppError(
    code = code,
    message = message,
    retryable = retryable,
    httpStatus = httpStatus,
    providerId = providerId,
    model = model,
    requestId = requestId,
    cause = cause,
)

fun cancelledError(): AppError = appError(ErrorCode.CANCELLED, "Generation cancelled.")

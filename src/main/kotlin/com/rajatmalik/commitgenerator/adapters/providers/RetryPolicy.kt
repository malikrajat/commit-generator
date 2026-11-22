package com.rajatmalik.commitgenerator.adapters.providers

import com.rajatmalik.commitgenerator.core.domain.AppError
import com.rajatmalik.commitgenerator.core.domain.CancelSignal
import kotlin.math.min

const val MAX_BACKOFF_MS = 8_000L
private const val BASE_DELAY_MS = 500L

fun parseRetryAfterMs(headers: Map<String, String>): Long? {
    val raw = headers["retry-after"] ?: return null
    val seconds = raw.toLongOrNull()
    if (seconds != null && seconds >= 0) {
        return min(MAX_BACKOFF_MS, seconds * 1000)
    }
    val date = runCatching { java.time.ZonedDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
        ?: return null
    return min(MAX_BACKOFF_MS, maxOf(0L, date - System.currentTimeMillis()))
}

fun shouldRetry(error: AppError, attempt: Int, maxRetries: Int): Boolean =
    error.retryable && attempt < maxRetries

fun backoffMs(attempt: Int, retryAfterMs: Long? = null): Long {
    retryAfterMs?.let { return it }
    val exponential = BASE_DELAY_MS * (1L shl maxOf(0, attempt))
    val jitter = (0..250).random().toLong()
    return min(MAX_BACKOFF_MS, exponential + jitter)
}

fun abortableDelay(ms: Long, signal: CancelSignal) {
    if (signal.isCancelled || ms <= 0) {
        return
    }
    val latch = java.util.concurrent.CountDownLatch(1)
    val subscription = signal.onCancel { latch.countDown() }
    try {
        latch.await(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    } finally {
        subscription.close()
    }
}

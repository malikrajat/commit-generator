package com.rajatmalik.commitgenerator.core.domain

/**
 * Explicit result type used across the core and adapters. It is deliberately
 * not `kotlin.Result`, because every failure must carry a normalized error.
 */
sealed interface CwResult<out T> {
    data class Ok<T>(val value: T) : CwResult<T>
    data class Err(val error: AppError) : CwResult<Nothing>
}

fun <T> ok(value: T): CwResult<T> = CwResult.Ok(value)

fun <T> err(error: AppError): CwResult<T> = CwResult.Err(error)

inline fun <T, R> CwResult<T>.map(transform: (T) -> R): CwResult<R> = when (this) {
    is CwResult.Ok -> CwResult.Ok(transform(value))
    is CwResult.Err -> this
}

inline fun <T, R> CwResult<T>.flatMap(transform: (T) -> CwResult<R>): CwResult<R> = when (this) {
    is CwResult.Ok -> transform(value)
    is CwResult.Err -> this
}

inline fun <T> CwResult<T>.onOk(action: (T) -> Unit): CwResult<T> {
    if (this is CwResult.Ok) {
        action(value)
    }
    return this
}

inline fun <T> CwResult<T>.onErr(action: (AppError) -> Unit): CwResult<T> {
    if (this is CwResult.Err) {
        action(error)
    }
    return this
}

fun <T> CwResult<T>.valueOrNull(): T? = (this as? CwResult.Ok)?.value

/** Precondition: caller already handled the error branch. */
fun <T> CwResult<T>.valueUnsafe(): T = (this as CwResult.Ok).value

fun <T> CwResult<T>.errorOrNull(): AppError? = (this as? CwResult.Err)?.error

fun <T> CwResult<T>.valueOr(defaultValue: T): T = (this as? CwResult.Ok)?.value ?: defaultValue

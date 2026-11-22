package com.rajatmalik.commitgenerator.core.domain

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core cancellation primitive. Adapters bridge IntelliJ's ProgressIndicator to
 * this interface so the HTTP layer and the summary engine can stop promptly.
 */
interface CancelSignal {
    val isCancelled: Boolean
    fun onCancel(listener: () -> Unit): AutoCloseable
}

object NeverCancelled : CancelSignal {
    override val isCancelled: Boolean = false
    override fun onCancel(listener: () -> Unit): AutoCloseable = AutoCloseable { }
}

class CancellationToken : CancelSignal {
    private val cancelled = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    override val isCancelled: Boolean
        get() = cancelled.get()

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return
        }
        for (listener in listeners) {
            runCatching(listener)
        }
        listeners.clear()
    }

    override fun onCancel(listener: () -> Unit): AutoCloseable {
        if (cancelled.get()) {
            runCatching(listener)
            return AutoCloseable { }
        }
        listeners.add(listener)
        if (cancelled.get() && listeners.remove(listener)) {
            runCatching(listener)
        }
        return AutoCloseable { listeners.remove(listener) }
    }
}
